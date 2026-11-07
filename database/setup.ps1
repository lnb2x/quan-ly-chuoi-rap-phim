param(
    [ValidateSet("Normal", "CleanReset")]
    [string]$Mode = "Normal",

    [switch]$CleanReset
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

if ($CleanReset) {
    $Mode = "CleanReset"
}

$rootDir = Split-Path -Parent $PSScriptRoot

# =============================================================================
# 1. Định vị MySQL Community Server 8.4 LTS chính thức (Tuyệt đối không chạm XAMPP)
# =============================================================================
$mysqlDir = Join-Path $env:USERPROFILE ".mysql\mysql-8.4"
if (-not (Test-Path (Join-Path $mysqlDir "bin\mysql.exe"))) {
    $progMysql = Get-ChildItem "C:\Program Files\MySQL\MySQL Server 8*" -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($progMysql) {
        $mysqlDir = $progMysql.FullName
    } else {
        throw "Không tìm thấy MySQL Server 8.x tại $mysqlDir hoặc C:\Program Files\MySQL."
    }
}

$mysqlExe  = Join-Path $mysqlDir "bin\mysql.exe"
$mysqldExe = Join-Path $mysqlDir "bin\mysqld.exe"
$myIni     = Join-Path $mysqlDir "my.ini"

function Test-MySqlPortListening([string]$hostName = "127.0.0.1", [int]$port = 3306) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect($hostName, $port, $null, $null)
        if ($async.AsyncWaitHandle.WaitOne(800, $false) -and $client.Connected) {
            $client.EndConnect($async)
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

if (-not (Test-MySqlPortListening "127.0.0.1" 3306)) {
    if (Test-Path $myIni) {
        Write-Output "Đang khởi động MySQL Community Server 8.4 LTS..."
        Start-Process -FilePath $mysqldExe -ArgumentList "--defaults-file=$myIni" -WindowStyle Hidden
        Start-Sleep -Seconds 4
    } else {
        throw "MySQL Server chưa lắng nghe trên cổng 3306 và không tìm thấy tệp cấu hình $myIni."
    }
}

# =============================================================================
# 2. Đọc thông tin xác thực từ biến môi trường hoặc config/application.properties
# =============================================================================
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
    throw "Chưa cấu hình mật khẩu MySQL cho ứng dụng. Vui lòng đặt biến môi trường CINEMA_DB_PASSWORD hoặc cấu hình db.password trong config/application.properties."
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
            throw "Truy vấn MySQL thất bại (Mã thoát: $LASTEXITCODE)"
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

function Provision-LeastPrivilegeAppUser([string]$user, [string]$pass, [string]$rootPwd) {
    $escapedPass = $pass.Replace("'", "''")
    $provisionSql = @"
CREATE USER IF NOT EXISTS '$user'@'localhost' IDENTIFIED BY '$escapedPass';
ALTER USER '$user'@'localhost' IDENTIFIED BY '$escapedPass';
CREATE USER IF NOT EXISTS '$user'@'127.0.0.1' IDENTIFIED BY '$escapedPass';
ALTER USER '$user'@'127.0.0.1' IDENTIFIED BY '$escapedPass';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO '$user'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE ON cinema_chain.* TO '$user'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE, CREATE, DROP, ALTER, INDEX, TRIGGER, CREATE VIEW ON cinema_chain_test.* TO '$user'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, SHOW VIEW, EXECUTE, CREATE, DROP, ALTER, INDEX, TRIGGER, CREATE VIEW ON cinema_chain_test.* TO '$user'@'127.0.0.1';
FLUSH PRIVILEGES;
"@
    Invoke-MySqlQuery $provisionSql "root" $rootPwd | Out-Null
}

function Get-DatabaseSummary([string]$dbName, [string]$rootPwd) {
    $exists = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$dbName';" "root" $rootPwd) -join "")
    if ($exists -eq 0) {
        return [PSCustomObject]@{ Exists = $false; Tables = 0; ForeignKeys = 0; Triggers = 0; Views = 0; TotalRows = 0; Detail = "Chưa tồn tại" }
    }
    $tblCount = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$dbName' AND TABLE_TYPE = 'BASE TABLE';" "root" $rootPwd) -join "")
    $fkCount  = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$dbName';" "root" $rootPwd) -join "")
    $trgCount = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '$dbName';" "root" $rootPwd) -join "")
    $vwCount  = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '$dbName';" "root" $rootPwd) -join "")

    $totalRows = 0
    $detailParts = @()
    if ($tblCount -gt 0) {
        foreach ($tbl in @("cinema_branch", "screening_room", "movie", "screening", "booking", "ticket", "payment")) {
            $hasTbl = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$dbName' AND TABLE_NAME = '$tbl';" "root" $rootPwd) -join "")
            if ($hasTbl -eq 1) {
                $cnt = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM $tbl;" "root" $rootPwd $dbName) -join "")
                $totalRows += $cnt
                $detailParts += "$tbl=$cnt"
            }
        }
    }
    $detailStr = if ($detailParts.Count -gt 0) { $detailParts -join ", " } else { "0 bản ghi" }
    return [PSCustomObject]@{
        Exists      = $true
        Tables      = $tblCount
        ForeignKeys = $fkCount
        Triggers    = $trgCount
        Views       = $vwCount
        TotalRows   = $totalRows
        Detail      = "$tblCount bảng, $fkCount FK, $trgCount Trigger, $vwCount View | Dữ liệu: $detailStr"
    }
}

function Initialize-DatabaseNonDestructive([string]$targetDb, [string]$rootPwd) {
    $before = Get-DatabaseSummary $targetDb $rootPwd

    Write-Output "  -> [$targetDb] Kiểm tra/tạo 13 bảng chuẩn 3NF (CREATE TABLE IF NOT EXISTS, không xóa bảng)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01_schema.sql" "root" $rootPwd $targetDb | Out-Null

    $fkCount = [int]((Invoke-MySqlQuery "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$targetDb';" "root" $rootPwd) -join "")
    if ($fkCount -eq 0) {
        Write-Output "  -> [$targetDb] Chưa có khóa ngoại -> Thiết lập đủ 16 ràng buộc FOREIGN KEY (01b_foreign_keys.sql)..."
        Invoke-MySqlFile "$PSScriptRoot\schema\01b_foreign_keys.sql" "root" $rootPwd $targetDb | Out-Null
    } elseif ($fkCount -lt 16) {
        Write-Output "  -> [$targetDb] Đang có $fkCount/16 khóa ngoại -> Bổ sung các khóa ngoại còn thiếu..."
        $existingFks = @(Invoke-MySqlQuery "SELECT CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$targetDb';" "root" $rootPwd)
        $fkText = Get-Content "$PSScriptRoot\schema\01b_foreign_keys.sql" -Raw -Encoding UTF8
        $statements = $fkText -split ";"
        foreach ($stmt in $statements) {
            if ($stmt -match "ALTER TABLE\s+(\w+)") {
                $tblName = $Matches[1]
                $clauseMatches = [regex]::Matches($stmt, "ADD CONSTRAINT\s+(fk_\w+)\s+FOREIGN KEY\s*\([^)]+\)\s*REFERENCES\s+\w+\s*\([^)]+\)\s*ON DELETE\s+[A-Z ]+ON UPDATE\s+[A-Z]+")
                foreach ($m in $clauseMatches) {
                    $fkName = $m.Groups[1].Value
                    if ($existingFks -notcontains $fkName) {
                        Invoke-MySqlQuery "ALTER TABLE $tblName $($m.Value);" "root" $rootPwd $targetDb | Out-Null
                    }
                }
            }
        }
    } else {
        Write-Output "  -> [$targetDb] Đã tồn tại đủ 16/16 khóa ngoại (FOREIGN KEY) — giữ nguyên."
    }

    Write-Output "  -> [$targetDb] Đồng bộ 06 Trigger nghiệp vụ (02_triggers.sql) & 04 View báo cáo (03_views.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\02_triggers.sql" "root" $rootPwd $targetDb | Out-Null
    Invoke-MySqlFile "$PSScriptRoot\schema\03_views.sql" "root" $rootPwd $targetDb | Out-Null

    $afterSchema = Get-DatabaseSummary $targetDb $rootPwd
    if ($afterSchema.TotalRows -eq 0) {
        Write-Output "  -> [$targetDb] CSDL đang rỗng (0 bản ghi) -> Nạp bộ dữ liệu mẫu khởi tạo ban đầu (10_seed.sql)..."
        Invoke-MySqlFile "$PSScriptRoot\seed\10_seed.sql" "root" $rootPwd $targetDb | Out-Null
    } else {
        Write-Output "  -> [$targetDb] BẢO TOÀN DỮ LIỆU HIỆN CÓ ($($before.Detail)) — Tuyệt đối KHÔNG xóa và KHÔNG ghi đè dữ liệu mẫu!"
    }
}

# =============================================================================
# 3. Kiểm chứng tính tương thích MySQL Community Server 8.4 LTS (Chặn MariaDB)
# =============================================================================
$serverVersion = (Invoke-MySqlQuery "SELECT CONCAT(VERSION(), ' | ', @@version_comment);" "root" $rootPass) -join " "
Write-Output "======================================================================"
Write-Output " HỆ THỐNG KHỞI TẠO CSDL CHUỖI RẠP CHIẾU PHIM (CHẾ ĐỘ: $($Mode.ToUpper()))"
Write-Output " Hệ quản trị kết nối: $serverVersion"
Write-Output "======================================================================"

if ($serverVersion -match "(?i)mariadb") {
    throw "LỖI NGHIÊM TRỌNG: Máy chủ trên cổng 3306 là MariaDB ($serverVersion). Dự án bắt buộc chạy trên MySQL Community Server 8.x!"
}
$verNumber = ($serverVersion -split "\|")[0].Trim()
$majorVer = [int](($verNumber -split "\.")[0])
if ($majorVer -lt 8) {
    throw "LỖI PHIÊN BẢN: Yêu cầu MySQL Server >= 8.0 (khuyến nghị 8.4 LTS). Thực tế: $verNumber"
}

# Chụp danh sách các CSDL ngoài dự án trước khi chạy để đối chứng không bị tác động
$preRunOtherDbs = @(Invoke-MySqlQuery "SHOW DATABASES;" "root" $rootPass | Where-Object { $_ -ne "cinema_chain" -and $_ -ne "cinema_chain_test" })

$logFileName = if ($Mode -eq "CleanReset") { "db_clean_reset_verify.log" } else { "db_setup_and_verify.log" }
$logFile = Join-Path $rootDir "verification\logs\$logFileName"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null

# =============================================================================
# 4. Thực thi theo Chế độ: CLEAN RESET hoặc NORMAL SETUP
# =============================================================================
if ($Mode -eq "CleanReset") {
    $cleanTargetDb = "cinema_chain"
    # Chốt chặn an toàn cứng: chỉ cho phép xóa duy nhất database cinema_chain
    if ($cleanTargetDb -cne "cinema_chain") {
        throw "CHẶN AN TOÀN: Clean Reset CHỈ được phép thao tác trên database 'cinema_chain'!"
    }

    $currentSummary = Get-DatabaseSummary $cleanTargetDb $rootPass
    Write-Output ""
    Write-Output "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    Write-Output " [CẢNH BÁO XÁC NHẬN TRƯỚC KHI XÓA DỮ LIỆU — CLEAN RESET]"
    Write-Output " - Cơ sở dữ liệu sẽ bị XÓA VÀ DỰNG LẠI TỪ ĐẦU : $cleanTargetDb"
    Write-Output " - Trạng thái hiện tại của $cleanTargetDb      : $($currentSummary.Detail)"
    Write-Output " - Các CSDL khác trên MySQL ($($preRunOtherDbs -join ', ')) & XAMPP: KHÔNG BỊ TÁC ĐỘNG"
    Write-Output "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    $confirmInput = Read-Host "Nhập chính xác 'YES' để xác nhận xóa và tạo lại '$cleanTargetDb' từ đầu (nhập khác để hủy)"

    if ($confirmInput -cne "YES") {
        Write-Output "[ĐÃ HỦY AN TOÀN] Bạn đã nhập '$confirmInput' (không phải 'YES'). Giữ nguyên toàn bộ dữ liệu hiện có trong '$cleanTargetDb'."
        exit 0
    }

    "=== MYSQL 8.4 LTS SETUP & VERIFICATION LOG [MODE: CLEAN RESET] ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8
    $verTable = Invoke-MySqlQuery "SELECT VERSION() AS mysql_version, @@version_comment AS distribution, @@sql_mode AS sql_mode, @@character_set_server AS charset, @@collation_server AS collation;" "root" $rootPass -TableOutput
    $verTable | Out-File -FilePath $logFile -Append -Encoding utf8

    Write-Output ""
    Write-Output "[Bước 1/6] Xóa database '$cleanTargetDb' và khởi tạo lại mới từ đầu (00_clean_reset_cinema_chain.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\00_clean_reset_cinema_chain.sql" "root" $rootPass | Out-Null
    # Đảm bảo CSDL kiểm thử tự động tồn tại không phá hủy (không bao giờ DROP cinema_chain_test)
    Invoke-MySqlQuery "CREATE DATABASE IF NOT EXISTS cinema_chain_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" "root" $rootPass | Out-Null
    Provision-LeastPrivilegeAppUser $appUser $appPass $rootPass

    Write-Output "[Bước 2/6] Tạo cấu trúc 13 bảng chuẩn 3NF trên '$cleanTargetDb' (01_schema.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01_schema.sql" "root" $rootPass $cleanTargetDb | Out-Null

    Write-Output "[Bước 3/6] Thiết lập 16 ràng buộc Khóa ngoại FOREIGN KEY trên '$cleanTargetDb' (01b_foreign_keys.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01b_foreign_keys.sql" "root" $rootPass $cleanTargetDb | Out-Null

    Write-Output "[Bước 4/6] Thiết lập 06 Trigger kiểm tra toàn vẹn nghiệp vụ trên '$cleanTargetDb' (02_triggers.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\02_triggers.sql" "root" $rootPass $cleanTargetDb | Out-Null

    Write-Output "[Bước 5/6] Thiết lập 04 Khung nhìn (VIEW) tra cứu & báo cáo trên '$cleanTargetDb' (03_views.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\03_views.sql" "root" $rootPass $cleanTargetDb | Out-Null

    Write-Output "[Bước 6/6] Nạp dữ liệu mẫu chuẩn theo thứ tự Bảng Cha -> Bảng Con vào '$cleanTargetDb' (10_seed.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\seed\10_seed.sql" "root" $rootPass $cleanTargetDb | Out-Null

    # Nếu máy mới chưa từng khởi tạo cinema_chain_test thì khởi tạo không phá hủy cho cinema_chain_test
    $testSummary = Get-DatabaseSummary "cinema_chain_test" $rootPass
    if ($testSummary.Tables -lt 13 -or $testSummary.TotalRows -eq 0) {
        Write-Output "[Bổ trợ Kiểm thử] Khởi tạo không phá hủy cho CSDL kiểm thử 'cinema_chain_test'..."
        Initialize-DatabaseNonDestructive "cinema_chain_test" $rootPass
    }
}
else {
    "=== MYSQL 8.4 LTS SETUP & VERIFICATION LOG [MODE: NORMAL SETUP] ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8
    $verTable = Invoke-MySqlQuery "SELECT VERSION() AS mysql_version, @@version_comment AS distribution, @@sql_mode AS sql_mode, @@character_set_server AS charset, @@collation_server AS collation;" "root" $rootPass -TableOutput
    $verTable | Out-File -FilePath $logFile -Append -Encoding utf8

    Write-Output ""
    Write-Output "[Normal Setup - Bước 1] Đảm bảo CSDL dự án tồn tại & cấp quyền tối thiểu cho [$appUser] (KHÔNG xóa dữ liệu hiện có)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\00_init_databases_and_user.sql" "root" $rootPass | Out-Null
    Provision-LeastPrivilegeAppUser $appUser $appPass $rootPass

    Write-Output "[Normal Setup - Bước 2] Kiểm tra cấu trúc và bảo toàn dữ liệu trên 'cinema_chain'..."
    Initialize-DatabaseNonDestructive "cinema_chain" $rootPass

    Write-Output "[Normal Setup - Bước 3] Kiểm tra cấu trúc và bảo toàn dữ liệu trên 'cinema_chain_test'..."
    Initialize-DatabaseNonDestructive "cinema_chain_test" $rootPass
}

# =============================================================================
# 5. Đối chứng Metadata MySQL 8.4, An toàn CSDL khác & Toàn vẹn Nghiệp vụ
# =============================================================================
$postRunOtherDbs = @(Invoke-MySqlQuery "SHOW DATABASES;" "root" $rootPass | Where-Object { $_ -ne "cinema_chain" -and $_ -ne "cinema_chain_test" })
if (($preRunOtherDbs -join ",") -ne ($postRunOtherDbs -join ",")) {
    throw "CẢNH BÁO AN TOÀN: Danh sách CSDL ngoài dự án thay đổi bất thường!"
}

$finalSummary = Get-DatabaseSummary "cinema_chain" $rootPass
Write-Output ""
Write-Output "[Kiểm tra Siêu dữ liệu MySQL 8.4 trên 'cinema_chain'] $($finalSummary.Detail)"
if ($finalSummary.Tables -ne 13 -or $finalSummary.ForeignKeys -ne 16 -or $finalSummary.Triggers -ne 6 -or $finalSummary.Views -ne 4) {
    throw "LỖI CẤU TRÚC: Kỳ vọng 13 bảng, 16 FK, 6 Trigger, 4 View nhưng thực tế là: $($finalSummary.Detail)"
}
"--- METADATA SUMMARY ON MYSQL 8.4 ---`n$($finalSummary.Detail)" | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "[Kiểm chứng Toàn vẹn bằng tài khoản ứng dụng '$appUser' trên 'cinema_chain']..."
"--- VERIFY CONSTRAINTS OUTPUT ON MYSQL 8.4 (connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut = Invoke-MySqlFile "$PSScriptRoot\verification\verify_constraints.sql" $appUser $appPass "cinema_chain" -TableOutput
$verifyOut | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut | Write-Output

"--- UTF-8 VIETNAMESE DIACRITICS & MYSQL VERSION CHECK (connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$utfCheck = Invoke-MySqlQuery "SELECT VERSION() AS active_mysql_version, CURRENT_USER() AS connected_user; SELECT branch_id, branch_code, branch_name, city FROM cinema_branch ORDER BY branch_id;" $appUser $appPass "cinema_chain" -TableOutput
$utfCheck | Out-File -FilePath $logFile -Append -Encoding utf8

"--- BUSINESS QUERIES SAMPLE OUTPUT ON MYSQL 8.4 (connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$bizOut = Invoke-MySqlFile "$PSScriptRoot\queries\business_queries.sql" $appUser $appPass "cinema_chain" -TableOutput
$bizOut | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "Hoàn tất chế độ [$Mode] thành công trên MySQL Community Server 8.4 LTS. Nhật ký lưu tại: $logFile"
