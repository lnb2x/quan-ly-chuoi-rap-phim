$ErrorActionPreference = "Stop"
$mysqlExe = "C:\xampp\mysql\bin\mysql.exe"
$mysqldExe = "C:\xampp\mysql\bin\mysqld.exe"
$myIni = "C:\xampp\mysql\bin\my.ini"

if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    Write-Output "Starting XAMPP MariaDB server..."
    Start-Process -FilePath $mysqldExe -ArgumentList "--defaults-file=$myIni","--standalone" -WindowStyle Hidden
    Start-Sleep -Seconds 3
}

$rootDir = Split-Path -Parent $PSScriptRoot
$logFile = Join-Path $rootDir "verification\logs\db_setup_and_verify.log"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null

"=== DATABASE SETUP & VERIFICATION LOG ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8

Write-Output "1. Initializing databases and least-privilege user cinema_app..."
Get-Content "$PSScriptRoot\schema\00_init_databases_and_user.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -u root -h 127.0.0.1 -P 3306

foreach ($dbName in @("cinema_chain", "cinema_chain_test")) {
    Write-Output "2. Applying schema, triggers, views, and seed data to [$dbName]..."
    Get-Content "$PSScriptRoot\schema\01_schema.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -u root -h 127.0.0.1 -P 3306 $dbName
    Get-Content "$PSScriptRoot\schema\02_triggers.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -u root -h 127.0.0.1 -P 3306 $dbName
    Get-Content "$PSScriptRoot\schema\03_views.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -u root -h 127.0.0.1 -P 3306 $dbName
    Get-Content "$PSScriptRoot\seed\10_seed.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -u root -h 127.0.0.1 -P 3306 $dbName
}

Write-Output "3. Running verification queries as least-privilege user [cinema_app]..."
"--- VERIFY CONSTRAINTS OUTPUT (connected as cinema_app) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut = Get-Content "$PSScriptRoot\verification\verify_constraints.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -t -u cinema_app "-pCinemaApp@2026!" -h 127.0.0.1 -P 3306 cinema_chain
$verifyOut | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut | Write-Output

"--- BUSINESS QUERIES SAMPLE OUTPUT (connected as cinema_app) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$bizOut = Get-Content "$PSScriptRoot\queries\business_queries.sql" -Raw -Encoding UTF8 | & $mysqlExe --default-character-set=utf8mb4 -t -u cinema_app "-pCinemaApp@2026!" -h 127.0.0.1 -P 3306 cinema_chain
$bizOut | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "Database setup and verification completed. Log saved to $logFile"
