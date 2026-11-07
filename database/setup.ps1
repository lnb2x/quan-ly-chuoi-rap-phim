param(
    [ValidateSet("Normal", "ResetTestDb", "CleanReset")]
    [string]$Mode = "Normal",

    [string]$TargetDatabase = "cinema_chain_test",

    [switch]$ResetTestDb,

    [switch]$CleanReset,

    [switch]$ConfirmYes
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

# =============================================================================
# 0. Chốt chặn an toàn cứng trước khi mở bất kỳ kết nối nào tới MySQL
#    Tuyệt đối cấm xóa/reset `cinema_chain` trong mọi workflow phát triển/kiểm thử!
# =============================================================================
if ($CleanReset -or $Mode -eq "CleanReset") {
    throw "CHẶN AN TOÀN TUYỆT ĐỐI: Chế độ -CleanReset nhắm vào CSDL chính đã bị VÔ HIỆU HÓA để bảo vệ dữ liệu 'cinema_chain'. Để làm mới môi trường kiểm thử, chỉ được phép dùng: -ResetTestDb -TargetDatabase cinema_chain_test"
}

if ($ResetTestDb) {
    $Mode = "ResetTestDb"
}

if ($TargetDatabase -notin @("cinema_chain_test", "cinema_chain")) {
    throw "CHẶN AN TOÀN: TargetDatabase '$TargetDatabase' không hợp lệ. Chỉ chấp nhận 'cinema_chain_test' (mặc định) hoặc 'cinema_chain' (chỉ khi khởi tạo mới lần đầu trên máy trống)."
}

if ($Mode -eq "ResetTestDb" -and $TargetDatabase -cne "cinema_chain_test") {
    throw "CHẶN AN TOÀN TUYỆT ĐỐI: Tác vụ Reset chỉ được phép nhắm vào duy nhất 'cinema_chain_test', tuyệt đối cấm chạy trên '$TargetDatabase'!"
}

$rootDir = Split-Path -Parent $PSScriptRoot

# =============================================================================
# 1. Định vị đúng MySQL Community Server 8.4 LTS (Tuyệt đối không chạm XAMPP)
# =============================================================================
$mysqlDir        = Join-Path $env:USERPROFILE ".mysql\mysql-8.4"
$expectedDataDir = (Join-Path $env:USERPROFILE ".mysql\data-8.4").Replace('\', '/').TrimEnd('/')
$mysqlExe        = if ($env:CINEMA_MYSQL_EXE_OVERRIDE) { $env:CINEMA_MYSQL_EXE_OVERRIDE } else { Join-Path $mysqlDir "bin\mysql.exe" }

if (-not (Test-Path $mysqlExe)) {
    throw "Không tìm thấy MySQL Community Server 8.4 LTS tại $mysqlExe."
}

if (-not $env:CINEMA_MYSQL_EXE_OVERRIDE) {
    powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\start_mysql84.ps1"
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể xác minh hoặc khởi động đúng instance MySQL 8.4.0 LTS."
    }
}

# =============================================================================
# 2. Đọc thông tin xác thực cục bộ & truyền qua --defaults-extra-file + stdin
#    (Tuyệt đối không truyền mật khẩu hay IDENTIFIED BY trên dòng lệnh tiến trình)
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
    throw "Chưa cấu hình mật khẩu MySQL cho ứng dụng trong CINEMA_DB_PASSWORD hoặc config/application.properties."
}

function New-RestrictedMySqlDefaultsFile([string]$user, [string]$pass) {
    $tmpPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cinema_setup_" + [System.Guid]::NewGuid().ToString("N") + ".cnf")
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

function Provision-LeastPrivilegeAppUser([string]$user, [string]$pass, [string]$rootPwd) {
    $escapedPass = $pass.Replace("'", "''")
    # Chỉ cấp quyền qua stdin, tuyệt đối không xuất hiện trên tham số dòng lệnh tiến trình
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
    Invoke-MySqlStdin $provisionSql "root" $rootPwd | Out-Null
}

function Get-DatabaseSummary([string]$dbName, [string]$rootPwd) {
    $exists = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$dbName';" "root" $rootPwd) -join "")
    if ($exists -eq 0) {
        return [PSCustomObject]@{ Exists = $false; Tables = 0; ForeignKeys = 0; Triggers = 0; Views = 0; TotalRows = 0; Detail = "Chưa tồn tại" }
    }
    $tblCount = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$dbName' AND TABLE_TYPE = 'BASE TABLE';" "root" $rootPwd) -join "")
    $fkCount  = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$dbName';" "root" $rootPwd) -join "")
    $trgCount = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = '$dbName';" "root" $rootPwd) -join "")
    $vwCount  = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.VIEWS WHERE TABLE_SCHEMA = '$dbName';" "root" $rootPwd) -join "")

    $totalRows = 0
    $detailParts = @()
    if ($tblCount -gt 0) {
        foreach ($tbl in @("cinema_branch", "screening_room", "movie", "screening", "booking", "ticket", "screening_seat_lock", "payment")) {
            $hasTbl = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$dbName' AND TABLE_NAME = '$tbl';" "root" $rootPwd) -join "")
            if ($hasTbl -eq 1) {
                $cnt = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM $tbl;" "root" $rootPwd $dbName) -join "")
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

function Initialize-DatabaseNonDestructive([string]$dbName, [string]$rootPwd) {
    $before = Get-DatabaseSummary $dbName $rootPwd

    if ($dbName -ceq "cinema_chain" -and $before.Tables -gt 0) {
        throw "CHẶN AN TOÀN: CSDL chính 'cinema_chain' đã tồn tại dữ liệu ($($before.Detail)). Tuyệt đối không tự động ghi đè schema/trigger/view của 'cinema_chain' qua setup.ps1! Vui lòng sử dụng gói migration có kiểm soát tại database/migrations/ sau khi đã sao lưu và xác nhận."
    }

    Write-Output "  -> [$dbName] Khởi tạo 14 bảng chuẩn 3NF (CREATE TABLE IF NOT EXISTS)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01_schema.sql" "root" $rootPwd $dbName | Out-Null

    $fkCount = [int]((Invoke-MySqlStdin "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$dbName';" "root" $rootPwd) -join "")
    if ($fkCount -eq 0) {
        Write-Output "  -> [$dbName] Thiết lập đủ 17 ràng buộc FOREIGN KEY (01b_foreign_keys.sql)..."
        Invoke-MySqlFile "$PSScriptRoot\schema\01b_foreign_keys.sql" "root" $rootPwd $dbName | Out-Null
    } elseif ($fkCount -lt 17) {
        $existingFks = @(Invoke-MySqlStdin "SELECT CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = '$dbName';" "root" $rootPwd)
        $fkText = Get-Content "$PSScriptRoot\schema\01b_foreign_keys.sql" -Raw -Encoding UTF8
        foreach ($stmt in ($fkText -split ";")) {
            if ($stmt -match "ALTER TABLE\s+(\w+)") {
                $tblName = $Matches[1]
                $clauseMatches = [regex]::Matches($stmt, "ADD CONSTRAINT\s+(fk_\w+)\s+FOREIGN KEY\s*\([^)]+\)\s*REFERENCES\s+\w+\s*\([^)]+\)\s*ON DELETE\s+[A-Z ]+ON UPDATE\s+[A-Z]+")
                foreach ($m in $clauseMatches) {
                    $fkName = $m.Groups[1].Value
                    if ($existingFks -notcontains $fkName) {
                        Invoke-MySqlStdin "ALTER TABLE $tblName $($m.Value);" "root" $rootPwd $dbName | Out-Null
                    }
                }
            }
        }
    }

    Write-Output "  -> [$dbName] Thiết lập 12 Trigger nghiệp vụ (02_triggers.sql) & 04 View báo cáo (03_views.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\02_triggers.sql" "root" $rootPwd $dbName | Out-Null
    Invoke-MySqlFile "$PSScriptRoot\schema\03_views.sql" "root" $rootPwd $dbName | Out-Null

    $afterSchema = Get-DatabaseSummary $dbName $rootPwd
    if ($afterSchema.TotalRows -eq 0) {
        Write-Output "  -> [$dbName] CSDL đang rỗng -> Nạp dữ liệu mẫu chuẩn 3NF (10_seed.sql)..."
        Invoke-MySqlFile "$PSScriptRoot\seed\10_seed.sql" "root" $rootPwd $dbName | Out-Null
    } else {
        Write-Output "  -> [$dbName] Bảo toàn nguyên vẹn dữ liệu hiện có ($($before.Detail))."
    }
}

# =============================================================================
# 3. Xác minh trực tiếp phiên bản và datadir MySQL 8.4 LTS
# =============================================================================
$serverCheck = (Invoke-MySqlStdin "SELECT CONCAT(VERSION(), '|', @@version_comment, '|', REPLACE(@@datadir, '\\', '/'));" "root" $rootPass) -join ""
$sParts = $serverCheck.Split("|")
$verNumber = $sParts[0].Trim()
$verComment = $sParts[1].Trim()
$liveDataDir = $sParts[2].Trim().TrimEnd('/')

if ($verComment -match "(?i)mariadb" -or $verNumber -notlike "8.4.*" -or $liveDataDir.ToLower() -ne $expectedDataDir.ToLower()) {
    throw "LỖI INSTANCE MYSQL: Đang kết nối tới '$verNumber ($verComment)' tại '$liveDataDir', không khớp chuẩn MySQL 8.4 LTS tại '$expectedDataDir'."
}

Write-Output "======================================================================"
Write-Output " KHỞI TẠO MÔI TRƯỜNG CSDL (MODE: $($Mode.ToUpper()) | TARGET: $TargetDatabase)"
Write-Output " Instance xác thực: $verNumber ($verComment) | DataDir: $liveDataDir"
Write-Output "======================================================================"

$preRunOtherDbs = @(Invoke-MySqlStdin "SHOW DATABASES;" "root" $rootPass | Where-Object { $_ -ne $TargetDatabase })
$logFileName = if ($Mode -eq "ResetTestDb") { "db_reset_test_verify.log" } else { "db_setup_and_verify.log" }
$logFile = Join-Path $rootDir "verification\logs\$logFileName"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null

# =============================================================================
# 4. Thực thi theo Chế độ: RESET TEST DB (chỉ `cinema_chain_test`) hoặc NORMAL
# =============================================================================
if ($Mode -eq "ResetTestDb") {
    # Kiểm tra kép tên database thực tế trước khi thực hiện xóa
    if ($TargetDatabase -cne "cinema_chain_test") {
        throw "CHẶN AN TOÀN: Không được phép xóa '$TargetDatabase'!"
    }
    $existingTest = Get-DatabaseSummary "cinema_chain_test" $rootPass
    if ($existingTest.Exists) {
        $actualConnectedDb = (Invoke-MySqlStdin "SELECT DATABASE();" "root" $rootPass "cinema_chain_test") -join ""
        if ($actualConnectedDb.Trim() -cne "cinema_chain_test") {
            throw "LỖI XÁC THỰC DATABASE KẾT NỐI: Kỳ vọng 'cinema_chain_test', thực tế '$actualConnectedDb'."
        }
    }

    if (-not $ConfirmYes) {
        Write-Output " [XÁC NHẬN RESET CSDL KIỂM THỬ 'cinema_chain_test']"
        Write-Output " - Trạng thái hiện tại: $($existingTest.Detail)"
        Write-Output " - CSDL chính 'cinema_chain' và các CSDL khác: ĐƯỢC BẢO VỆ TUYỆT ĐỐI"
        $confirmInput = Read-Host "Nhập chính xác 'YES' để làm mới 'cinema_chain_test'"
        if ($confirmInput -cne "YES") {
            Write-Output "[ĐÃ HỦY AN TOÀN] Giữ nguyên trạng thái hiện tại của 'cinema_chain_test'."
            exit 0
        }
    }

    "=== MYSQL 8.4 LTS TEST DB PROVISIONING LOG [MODE: RESET TEST DB -> cinema_chain_test] ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8
    Invoke-MySqlStdin "SELECT VERSION() AS mysql_version, @@version_comment AS distribution, @@sql_mode AS sql_mode, @@character_set_server AS charset, @@collation_server AS collation;" "root" $rootPass -TableOutput | Out-File -FilePath $logFile -Append -Encoding utf8

    Write-Output "[Bước 1/6] Xóa và tạo mới duy nhất CSDL kiểm thử 'cinema_chain_test' (00_clean_reset_test_db.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\00_clean_reset_test_db.sql" "root" $rootPass | Out-Null
    Provision-LeastPrivilegeAppUser $appUser $appPass $rootPass

    Write-Output "[Bước 2/6] Tạo cấu trúc 14 bảng chuẩn 3NF trên 'cinema_chain_test' (01_schema.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01_schema.sql" "root" $rootPass "cinema_chain_test" | Out-Null

    Write-Output "[Bước 3/6] Thiết lập 17 ràng buộc FOREIGN KEY trên 'cinema_chain_test' (01b_foreign_keys.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\01b_foreign_keys.sql" "root" $rootPass "cinema_chain_test" | Out-Null

    Write-Output "[Bước 4/6] Thiết lập 12 Trigger nghiệp vụ trên 'cinema_chain_test' (02_triggers.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\02_triggers.sql" "root" $rootPass "cinema_chain_test" | Out-Null

    Write-Output "[Bước 5/6] Thiết lập 04 Khung nhìn (VIEW) trên 'cinema_chain_test' (03_views.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\schema\03_views.sql" "root" $rootPass "cinema_chain_test" | Out-Null

    Write-Output "[Bước 6/6] Nạp dữ liệu mẫu chuẩn 3NF vào 'cinema_chain_test' (10_seed.sql)..."
    Invoke-MySqlFile "$PSScriptRoot\seed\10_seed.sql" "root" $rootPass "cinema_chain_test" | Out-Null
}
else {
    "=== MYSQL 8.4 LTS SETUP LOG [MODE: NORMAL | TARGET: $TargetDatabase] ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8
    Invoke-MySqlStdin "CREATE DATABASE IF NOT EXISTS $TargetDatabase CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" "root" $rootPass | Out-Null
    Provision-LeastPrivilegeAppUser $appUser $appPass $rootPass
    Initialize-DatabaseNonDestructive $TargetDatabase $rootPass
}

# =============================================================================
# 5. Đối chứng Metadata 14 bảng chuẩn 3NF & Toàn vẹn Nghiệp vụ trên TargetDatabase
# =============================================================================
$postRunOtherDbs = @(Invoke-MySqlStdin "SHOW DATABASES;" "root" $rootPass | Where-Object { $_ -ne $TargetDatabase })
if (($preRunOtherDbs -join ",") -ne ($postRunOtherDbs -join ",")) {
    throw "CẢNH BÁO AN TOÀN: Danh sách các CSDL ngoài '$TargetDatabase' thay đổi bất thường!"
}

$finalSummary = Get-DatabaseSummary $TargetDatabase $rootPass
Write-Output ""
Write-Output "[Kiểm tra Siêu dữ liệu MySQL 8.4 trên '$TargetDatabase'] $($finalSummary.Detail)"
if ($finalSummary.Tables -ne 14 -or $finalSummary.ForeignKeys -ne 17 -or $finalSummary.Triggers -ne 12 -or $finalSummary.Views -ne 4) {
    throw "LỖI CẤU TRÚC: Kỳ vọng 14 bảng, 17 FK, 12 Trigger, 4 View nhưng thực tế là: $($finalSummary.Detail)"
}
"--- METADATA SUMMARY ON MYSQL 8.4 ($TargetDatabase) ---`n$($finalSummary.Detail)" | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "[Kiểm chứng Toàn vẹn bằng tài khoản ứng dụng '$appUser' trên '$TargetDatabase']..."
"--- VERIFY CONSTRAINTS OUTPUT ON MYSQL 8.4 ($TargetDatabase, connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut = Invoke-MySqlFile "$PSScriptRoot\verification\verify_constraints.sql" $appUser $appPass $TargetDatabase -TableOutput
$verifyOut | Out-File -FilePath $logFile -Append -Encoding utf8
$verifyOut | Write-Output

"--- BUSINESS QUERIES SAMPLE OUTPUT ON MYSQL 8.4 ($TargetDatabase, connected as $appUser) ---" | Out-File -FilePath $logFile -Append -Encoding utf8
$bizOut = Invoke-MySqlFile "$PSScriptRoot\queries\business_queries.sql" $appUser $appPass $TargetDatabase -TableOutput
$bizOut | Out-File -FilePath $logFile -Append -Encoding utf8

Write-Output "Hoàn tất chế độ [$Mode] trên '$TargetDatabase' thành công. Nhật ký lưu tại: $logFile"
