param(
    [switch]$ConfirmYes
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# =============================================================================
# TỆP: prepare_demo_db.ps1
# MỤC ĐÍCH: Kịch bản chuẩn bị chủ động (Explicit Demo Preparation) cho duy nhất
#           CSDL demo 'cinema_chain' với lược đồ 14 bảng chuẩn 3NF và dữ liệu mẫu.
# QUY TẮC AN TOÀN:
# - Chỉ được gọi khi người vận hành CHỦ ĐỘNG chuẩn bị lại CSDL demo trước buổi báo cáo.
# - Tuyệt đối KHÔNG được gọi từ com.cinema.app.Main, setup.ps1 hay verify_all.ps1.
# - Chỉ thao tác trên duy nhất 'cinema_chain'; bảo toàn nguyên vẹn 'cinema_chain_test'
#   và tất cả các cơ sở dữ liệu khác trên máy chủ MySQL 8.4 LTS.
# - Truyền thông tin đăng nhập qua --defaults-extra-file + stdin (không lộ mật khẩu CLI).
# =============================================================================

$TargetDatabase = "cinema_chain"
$rootDir        = Split-Path -Parent $PSScriptRoot
$mysqlDir       = Join-Path $env:USERPROFILE ".mysql\mysql-8.4"
$expectedDataDir = (Join-Path $env:USERPROFILE ".mysql\data-8.4").Replace('\', '/').TrimEnd('/')
$mysqlExe       = if ($env:CINEMA_MYSQL_EXE_OVERRIDE) { $env:CINEMA_MYSQL_EXE_OVERRIDE } else { Join-Path $mysqlDir "bin\mysql.exe" }

if (-not (Test-Path $mysqlExe)) {
    throw "Không tìm thấy MySQL Community Server 8.4 LTS tại $mysqlExe."
}

if (-not $ConfirmYes) {
    Write-Output "======================================================================"
    Write-Output " [XÁC NHẬN CHUẨN BỊ LẠI CSDL DEMO 'cinema_chain' — 14 BẢNG CHUẨN 3NF]"
    Write-Output " - CSDL mục tiêu duy nhất : cinema_chain (Xóa & tạo lại + nạp dữ liệu mẫu)"
    Write-Output " - Các CSDL khác trên máy : ĐƯỢC GIỮ NGUYÊN TUYỆT ĐỐI"
    Write-Output "======================================================================"
    $confirmInput = Read-Host "Nhập chính xác 'PREPARE_DEMO_DB' để tiếp tục (nhập khác để hủy)"
    if ($confirmInput -cne "PREPARE_DEMO_DB") {
        Write-Output "[ĐÃ HỦY AN TOÀN] Giữ nguyên trạng thái hiện tại của 'cinema_chain'."
        exit 0
    }
}

if (-not $env:CINEMA_MYSQL_EXE_OVERRIDE) {
    powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\start_mysql84.ps1"
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể xác minh hoặc khởi động đúng instance MySQL 8.4.0 LTS."
    }
}

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

$appUser  = if ($env:CINEMA_DB_USER)     { $env:CINEMA_DB_USER }     elseif ($propsMap["db.user"])     { $propsMap["db.user"] }     else { "cinema_app" }
$appPass  = if ($env:CINEMA_DB_PASSWORD) { $env:CINEMA_DB_PASSWORD } elseif ($propsMap["db.password"]) { $propsMap["db.password"] } else { "" }
$rootPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }

if ([string]::IsNullOrWhiteSpace($appPass) -or $appPass -eq "<YOUR_LOCAL_DB_PASSWORD>") {
    throw "Chưa cấu hình mật khẩu MySQL cho ứng dụng trong CINEMA_DB_PASSWORD hoặc config/application.properties."
}

function New-RestrictedMySqlDefaultsFile([string]$user, [string]$pass) {
    $tmpPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cinema_prep_demo_" + [System.Guid]::NewGuid().ToString("N") + ".cnf")
    $escaped = $pass.Replace('\', '\\').Replace('"', '\"')
    $cfgContent = "[client]`nhost=127.0.0.1`nport=3306`nprotocol=TCP`nuser=$user`npassword=`"$escaped`"`n[mysql]`ndefault-character-set=utf8mb4`n"
    [System.IO.File]::WriteAllText($tmpPath, $cfgContent, [System.Text.UTF8Encoding]::new($false))
    if ($env:USERNAME) {
        & icacls $tmpPath /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    }
    return $tmpPath
}

function Invoke-MySqlStdin([string]$sqlContent, [string]$user, [string]$pass, [string]$dbName = "", [switch]$TableOutput) {
    $cfgFile = New-RestrictedMySqlDefaultsFile $user $pass
    try {
        $argsList = @("--defaults-extra-file=$cfgFile")
        if ($TableOutput) { $argsList += "-t" } else { $argsList += @("-N", "-s") }
        if ($dbName -ne "") { $argsList += $dbName }
        $out = $sqlContent | & $mysqlExe @argsList
        if ($LASTEXITCODE -ne 0) {
            throw "Thực thi MySQL qua stdin thất bại (ExitCode=$LASTEXITCODE)."
        }
        return $out
    } finally {
        Remove-Item -LiteralPath $cfgFile -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-MySqlFile([string]$filePath, [string]$user, [string]$pass, [string]$dbName = "", [switch]$TableOutput) {
    $rawSql = [System.IO.File]::ReadAllText($filePath, [System.Text.Encoding]::UTF8)
    return Invoke-MySqlStdin $rawSql $user $pass $dbName -TableOutput:$TableOutput
}

$serverCheck = (Invoke-MySqlStdin "SELECT CONCAT(VERSION(), '|', @@version_comment, '|', REPLACE(@@datadir, '\\', '/'));" "root" $rootPass) -join ""
$sParts = $serverCheck.Split("|")
$verNumber = $sParts[0].Trim()
$verComment = $sParts[1].Trim()
$liveDataDir = $sParts[2].Trim().TrimEnd('/')

if ($verComment -match "(?i)mariadb" -or $verNumber -notlike "8.4.*" -or $liveDataDir.ToLower() -ne $expectedDataDir.ToLower()) {
    throw "LỖI INSTANCE MYSQL: Đang kết nối tới '$verNumber ($verComment)' tại '$liveDataDir', không khớp chuẩn MySQL 8.4 LTS tại '$expectedDataDir'."
}

$preOtherDbs = @(Invoke-MySqlStdin "SHOW DATABASES;" "root" $rootPass | Where-Object { $_ -cne "cinema_chain" })

$logFile = Join-Path $rootDir "verification\logs\cinema_chain_14table_provision.log"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null

Write-Output "======================================================================"
Write-Output " CHUẨN BỊ CSDL DEMO 'cinema_chain' (14 BẢNG CHUẨN 3NF — MYSQL $verNumber)"
Write-Output " DataDir: $liveDataDir | AppUser: $appUser (Least-Privilege)"
Write-Output "======================================================================"

"=== MYSQL 8.4 LTS DEMO DB PROVISIONING LOG [TARGET: cinema_chain] ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8
Invoke-MySqlStdin "SELECT VERSION() AS mysql_version, @@version_comment AS distribution, @@sql_mode AS sql_mode, @@character_set_server AS charset, @@collation_server AS collation;" "root" $rootPass -TableOutput | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "[Bước 1/6] Xóa và tạo mới duy nhất CSDL demo 'cinema_chain' (utf8mb4_unicode_ci) & cấp quyền tối thiểu '$appUser'..."
$escapedPass = $appPass.Replace("'", "''")
$initDemoSql = @"
DROP DATABASE IF EXISTS cinema_chain;
CREATE DATABASE cinema_chain
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$appUser'@'localhost' IDENTIFIED BY '$escapedPass';
ALTER USER '$appUser'@'localhost' IDENTIFIED BY '$escapedPass';
CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$escapedPass';
ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$escapedPass';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO '$appUser'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO '$appUser'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
Invoke-MySqlStdin $initDemoSql "root" $rootPass | Out-Null

Write-Output "[Bước 2/6] Tạo cấu trúc 14 bảng chuẩn 3NF trên 'cinema_chain' (01_schema.sql)..."
Invoke-MySqlFile "$PSScriptRoot\schema\01_schema.sql" "root" $rootPass "cinema_chain" | Out-Null

Write-Output "[Bước 3/6] Thiết lập 17 ràng buộc FOREIGN KEY trên 'cinema_chain' (01b_foreign_keys.sql)..."
Invoke-MySqlFile "$PSScriptRoot\schema\01b_foreign_keys.sql" "root" $rootPass "cinema_chain" | Out-Null

Write-Output "[Bước 4/6] Thiết lập 12 Trigger nghiệp vụ trên 'cinema_chain' (02_triggers.sql)..."
Invoke-MySqlFile "$PSScriptRoot\schema\02_triggers.sql" "root" $rootPass "cinema_chain" | Out-Null

Write-Output "[Bước 5/6] Thiết lập 04 Khung nhìn (VIEW) trên 'cinema_chain' (03_views.sql)..."
Invoke-MySqlFile "$PSScriptRoot\schema\03_views.sql" "root" $rootPass "cinema_chain" | Out-Null

Write-Output "[Bước 6/6] Nạp dữ liệu mẫu chuẩn 3NF vào 'cinema_chain' (10_seed.sql)..."
Invoke-MySqlFile "$PSScriptRoot\seed\10_seed.sql" "root" $rootPass "cinema_chain" | Out-Null

$postOtherDbs = @(Invoke-MySqlStdin "SHOW DATABASES;" "root" $rootPass | Where-Object { $_ -cne "cinema_chain" })
if (($preOtherDbs -join ",") -ne ($postOtherDbs -join ",")) {
    throw "CẢNH BÁO AN TOÀN: Danh sách các CSDL ngoài 'cinema_chain' thay đổi bất thường!"
}

$tblCount = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'cinema_chain' AND TABLE_TYPE = 'BASE TABLE';" "root" $rootPass) -join "")
$fkCount  = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = 'cinema_chain';" "root" $rootPass) -join "")
$trgCount = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = 'cinema_chain';" "root" $rootPass) -join "")
$vwCount  = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = 'cinema_chain';" "root" $rootPass) -join "")

$metaLine = "Tables=$tblCount | ForeignKeys=$fkCount | Triggers=$trgCount | Views=$vwCount"
Write-Output "[Kiểm tra Siêu dữ liệu MySQL 8.4 trên 'cinema_chain'] $metaLine"
if ($tblCount -ne 14 -or $fkCount -ne 17 -or $trgCount -ne 12 -or $vwCount -ne 4) {
    throw "LỖI CẤU TRÚC TRÊN 'cinema_chain': Kỳ vọng 14 bảng, 17 FK, 12 Trigger, 4 View nhưng thực tế là: $metaLine"
}

"--- METADATA SUMMARY ON MYSQL 8.4 (cinema_chain) ---`n$metaLine" | Out-File -FilePath $logFile -Append -Encoding utf8

"--- TABLE & COLUMN CHECK FOR 3NF TICKET / SCREENING_SEAT_LOCK (cinema_chain) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$colCheckSql = @"
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'cinema_chain'
  AND TABLE_NAME IN ('ticket', 'screening_seat_lock')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
"@
$colOut = Invoke-MySqlStdin $colCheckSql $appUser $appPass "cinema_chain" -TableOutput
$colOut | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "[Kiểm chứng Toàn vẹn bằng tài khoản ứng dụng '$appUser' trên 'cinema_chain']..."
"--- VERIFY CONSTRAINTS OUTPUT ON MYSQL 8.4 (cinema_chain, connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut = Invoke-MySqlFile "$PSScriptRoot\verification\verify_constraints.sql" $appUser $appPass "cinema_chain" -TableOutput
$verifyOut | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut | Write-Output

"--- BUSINESS QUERIES SAMPLE OUTPUT ON MYSQL 8.4 (cinema_chain, connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$bizOut = Invoke-MySqlFile "$PSScriptRoot\queries\business_queries.sql" $appUser $appPass "cinema_chain" -TableOutput
$bizOut | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "[DEMO DB READY] Đã hoàn tất chuẩn bị CSDL demo 'cinema_chain' (14 bảng chuẩn 3NF). Nhật ký lưu tại: $logFile"
