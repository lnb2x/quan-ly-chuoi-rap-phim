$ErrorActionPreference = "Stop"
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$rootDir = Split-Path -Parent $PSScriptRoot

# 1. Locate official MySQL 8.x client & server (never use XAMPP MariaDB)
$mysqlDir = Join-Path $env:USERPROFILE ".mysql\mysql-8.4"
if (-not (Test-Path (Join-Path $mysqlDir "bin\mysql.exe"))) {
    $progMysql = Get-ChildItem "C:\Program Files\MySQL\MySQL Server 8*" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($progMysql) {
        $mysqlDir = $progMysql.FullName
    } else {
        throw "Could not locate MySQL 8.x installation at $mysqlDir or C:\Program Files\MySQL."
    }
}

$mysqlExe  = Join-Path $mysqlDir "bin\mysql.exe"
$mysqldExe = Join-Path $mysqlDir "bin\mysqld.exe"
$myIni     = Join-Path $mysqlDir "my.ini"

if (-not (Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue)) {
    if (Test-Path $myIni) {
        Write-Output "Starting MySQL Community Server 8.4 LTS..."
        Start-Process -FilePath $mysqldExe -ArgumentList "--defaults-file=$myIni" -WindowStyle Hidden
        Start-Sleep -Seconds 4
    } else {
        throw "MySQL Server is not listening on port 3306 and $myIni was not found."
    }
}

# 2. Read credentials strictly from environment variables or local git-ignored config/application.properties
$localPropsPath = Join-Path $rootDir "config\application.properties"
$propsMap = @{}
if (Test-Path $localPropsPath) {
    foreach ($line in Get-Content $localPropsPath -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith("#") -and $trimmed.Contains("=")) {
            $parts = $trimmed.Split("=", 2)
            $propsMap[$parts[0].Trim()] = $parts[1].Trim()
        }
    }
}

$appUser = if ($env:CINEMA_DB_USER) { $env:CINEMA_DB_USER } elseif ($propsMap["db.user"]) { $propsMap["db.user"] } else { "cinema_app" }
$appPass = if ($env:CINEMA_DB_PASSWORD) { $env:CINEMA_DB_PASSWORD } elseif ($propsMap["db.password"]) { $propsMap["db.password"] } else { "" }
$rootPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }

if ([string]::IsNullOrWhiteSpace($appPass) -or $appPass -eq "<YOUR_LOCAL_DB_PASSWORD>") {
    throw "Missing MySQL application password. Set CINEMA_DB_PASSWORD or configure db.password in local config/application.properties."
}

function Invoke-MySqlQuery([string]$sql, [string]$user, [string]$pass, [string]$dbName = "", [switch]$TableOutput) {
    $oldPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $pass
        $argsList = @("--default-character-set=utf8mb4", "--protocol=TCP", "-h", "127.0.0.1", "-P", "3306", "-u", $user)
        if ($TableOutput) { $argsList += "-t" } else { $argsList += @("-N", "-s") }
        if ($dbName -ne "") { $argsList += $dbName }
        $argsList += @("-e", $sql)
        $out = & $mysqlExe @argsList
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL query failed (Exit code: $LASTEXITCODE)"
        }
        return $out
    } finally {
        $env:MYSQL_PWD = $oldPwd
    }
}

function Invoke-MySqlFile([string]$filePath, [string]$user, [string]$pass, [string]$dbName = "", [switch]$TableOutput) {
    $fwd = $filePath.Replace('\', '/')
    return Invoke-MySqlQuery "SOURCE $fwd" $user $pass $dbName -TableOutput:$TableOutput
}

# 3. Verify running server is genuine MySQL Server (reject MariaDB)
$serverVersion = (Invoke-MySqlQuery "SELECT CONCAT(VERSION(), ' | ', @@version_comment);" "root" $rootPass) -join " "
Write-Output "Connected DBMS: $serverVersion"
if ($serverVersion -match "(?i)mariadb") {
    throw "CRITICAL ERROR: Connected server is MariaDB ($serverVersion). Official MySQL Server is mandatory!"
}

$logFile = Join-Path $rootDir "verification\logs\db_setup_and_verify.log"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null

"=== MYSQL 8.4 LTS SETUP & VERIFICATION LOG ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8
"--- MYSQL SERVER RUNTIME IDENTITY ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$verTable = Invoke-MySqlQuery "SELECT VERSION() AS mysql_version, @@version_comment AS distribution, @@sql_mode AS sql_mode, @@character_set_server AS charset, @@collation_server AS collation;" "root" $rootPass -TableOutput
$verTable | Out-File -FilePath $logFile -Append -Encoding utf8
$verTable | Write-Output

Write-Output "1. Initializing project databases and least-privilege MySQL user [$appUser]..."
Invoke-MySqlFile "$PSScriptRoot\schema\00_init_databases_and_user.sql" "root" $rootPass | Out-Null

$escapedPass = $appPass.Replace("'", "''")
$provisionUserSql = @"
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$escapedPass';
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$escapedPass';
CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$escapedPass';
ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$escapedPass';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO '$appUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO '$appUser'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE, CREATE, DROP, ALTER, INDEX, TRIGGER, CREATE VIEW ON cinema_chain_test.* TO '$appUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE, CREATE, DROP, ALTER, INDEX, TRIGGER, CREATE VIEW ON cinema_chain_test.* TO '$appUser'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
Invoke-MySqlQuery $provisionUserSql "root" $rootPass | Out-Null

foreach ($dbName in @("cinema_chain", "cinema_chain_test")) {
    Write-Output "2. Applying MySQL 8.4 UTF-8 schema, triggers, views, and seed data to [$dbName]..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01_schema.sql" "root" $rootPass $dbName | Out-Null
    Invoke-MySqlFile "$PSScriptRoot\schema\02_triggers.sql" "root" $rootPass $dbName | Out-Null
    Invoke-MySqlFile "$PSScriptRoot\schema\03_views.sql" "root" $rootPass $dbName | Out-Null
    Invoke-MySqlFile "$PSScriptRoot\seed\10_seed.sql" "root" $rootPass $dbName | Out-Null
}

Write-Output "3. Running verification queries on MySQL 8.4 as least-privilege user [$appUser]..."
"--- VERIFY CONSTRAINTS OUTPUT ON MYSQL 8.4 (connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut = Invoke-MySqlFile "$PSScriptRoot\verification\verify_constraints.sql" $appUser $appPass "cinema_chain" -TableOutput
$verifyOut | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut | Write-Output

"--- UTF-8 VIETNAMESE DIACRITICS & MYSQL VERSION CHECK (connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$utfCheck = Invoke-MySqlQuery "SELECT VERSION() AS active_mysql_version, CURRENT_USER() AS connected_user; SELECT branch_id, branch_code, branch_name, city FROM cinema_branch ORDER BY branch_id; SELECT movie_id, movie_code, title FROM movie LIMIT 5;" $appUser $appPass "cinema_chain" -TableOutput
$utfCheck | Out-File -FilePath $logFile -Append -Encoding utf8
$utfCheck | Write-Output

"--- BUSINESS QUERIES SAMPLE OUTPUT ON MYSQL 8.4 (connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$bizOut = Invoke-MySqlFile "$PSScriptRoot\queries\business_queries.sql" $appUser $appPass "cinema_chain" -TableOutput
$bizOut | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "MySQL 8.4 setup and verification completed successfully. Log saved to $logFile"
