param(
    [ValidateSet("Up", "Rollback")]
    [string]$Direction = "Up",
    [switch]$MaintenanceConfirmed
)

$ErrorActionPreference = "Stop"
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$rootDir  = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dbDir    = Split-Path -Parent $PSScriptRoot
$mysqlBin = Join-Path $env:USERPROFILE ".mysql\mysql-8.4\bin"
$mysqlExe = Join-Path $mysqlBin "mysql.exe"
$targetDb = "cinema_chain"
$stamp    = (Get-Date -Format "yyyyMMdd_HHmmss") + "_" + [Guid]::NewGuid().ToString("N").Substring(0, 8)
$backupSql = Join-Path $rootDir "target\demo-backup\cinema_chain_before_migration_$stamp.sql"
$fingerprintJson = Join-Path $rootDir "target\demo-backup\cinema_chain_before_migration_$stamp.json"

if (-not $MaintenanceConfirmed) {
    throw "Cần -MaintenanceConfirmed sau khi đóng ứng dụng, callback server và mọi client ghi. Giữ các tiến trình này đóng tới khi đối soát xong; script không tự kill tiến trình."
}
$beforePhase = if ($Direction -eq "Up") { "pre" } else { "post" }
python "$PSScriptRoot\check_migration.py" --db $targetDb --phase $beforePhase --maintenance
if ($LASTEXITCODE -ne 0) { throw "Sai instance/lược đồ hoặc toàn vẹn không đạt; chưa thực hiện DDL. Với CSDL đã có 14 bảng, dùng refresh_triggers.py để cập nhật riêng trigger." }

Write-Output "======================================================================"
Write-Output " GÓI MIGRATION CÓ KIỂM SOÁT CHO CSDL CHÍNH 'cinema_chain' ($Direction)"
Write-Output " - Lưu ý: Script này KHÔNG xóa/reset dữ liệu, chỉ chuyển đổi cấu trúc tại chỗ."
Write-Output " - Bản sao lưu tự động sẽ được ghi tại: $backupSql"
Write-Output "======================================================================"

$expectedToken = if ($Direction -eq "Up") { "APPLY_3NF_MIGRATION" } else { "ROLLBACK_3NF_MIGRATION" }
$confirm = Read-Host "Nhập chính xác '$expectedToken' để thực thi trên 'cinema_chain' (nhập khác để hủy)"
if ($confirm -cne $expectedToken) {
    Write-Output "[ĐÃ HỦY AN TOÀN] Giữ nguyên trạng thái hiện tại của 'cinema_chain'."
    exit 0
}

function New-RestrictedMySqlDefaultsFile([string]$user, [string]$pass) {
    $tmpPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cinema_apply_" + [System.Guid]::NewGuid().ToString("N") + ".cnf")
    $escaped = $pass.Replace('\', '\\').Replace('"', '\"')
    $cfgContent = "[client]`nhost=127.0.0.1`nport=3306`nprotocol=TCP`nuser=$user`npassword=`"$escaped`"`n[mysql]`ndefault-character-set=utf8mb4`n"
    [System.IO.File]::WriteAllText($tmpPath, $cfgContent, [System.Text.UTF8Encoding]::new($false))
    if ($env:USERNAME) {
        & icacls $tmpPath /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Remove-Item -LiteralPath $tmpPath -Force
            throw "Không thể giới hạn quyền truy cập tệp xác thực MySQL."
        }
    }
    return $tmpPath
}

$rootPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }
$cfgFile  = New-RestrictedMySqlDefaultsFile "root" $rootPass

try {
    Write-Output "[1/4] Chụp dấu vân tay SHA-256 và sao lưu đầy đủ 'cinema_chain' trước khi thao tác..."
    python "$rootDir\tools\db_fingerprint.py" --db $targetDb --file $fingerprintJson --mode capture --use-root --dump-backup $backupSql
    if ($LASTEXITCODE -ne 0) { throw "Sao lưu trước migration thất bại!" }

    if ($Direction -eq "Up") {
        Write-Output "[2/4] Chạy kiểm tra điều kiện tiên quyết (V2_3nf_ticket_precheck.sql)..."
        [System.IO.File]::ReadAllText("$PSScriptRoot\V2_3nf_ticket_precheck.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" -t $targetDb
        if ($LASTEXITCODE -ne 0) { throw "Precheck thất bại." }
        python "$PSScriptRoot\check_migration.py" --db $targetDb --phase pre --maintenance
        if ($LASTEXITCODE -ne 0) { throw "Chỉ số precheck không đạt; dừng trước DDL." }

        Write-Output "[3/4] Thực thi Forward Migration 14 bảng chuẩn 3NF trên 'cinema_chain'..."
        [System.IO.File]::ReadAllText("$PSScriptRoot\V2_3nf_ticket_up.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $targetDb
        if ($LASTEXITCODE -ne 0) { throw "V2_3nf_ticket_up.sql thất bại." }
        [System.IO.File]::ReadAllText("$dbDir\schema\02_triggers.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $targetDb
        if ($LASTEXITCODE -ne 0) { throw "Cập nhật 02_triggers.sql thất bại." }
        [System.IO.File]::ReadAllText("$dbDir\schema\03_views.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $targetDb
        if ($LASTEXITCODE -ne 0) { throw "Cập nhật 03_views.sql thất bại." }

        Write-Output "[4/4] Đối soát toàn vẹn dữ liệu sau nâng cấp..."
        [System.IO.File]::ReadAllText("$dbDir\verification\verify_constraints.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" -t $targetDb
        if ($LASTEXITCODE -ne 0) { throw "Đối soát sau nâng cấp thất bại." }
        python "$PSScriptRoot\check_migration.py" --db $targetDb --phase post --maintenance
        if ($LASTEXITCODE -ne 0) { throw "Chỉ số sau nâng cấp không đạt." }
    } else {
        Write-Output "[2/4] Thực thi Rollback về lược đồ 13 bảng trên 'cinema_chain'..."
        [System.IO.File]::ReadAllText("$PSScriptRoot\V2_3nf_ticket_rollback.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $targetDb
        if ($LASTEXITCODE -ne 0) { throw "V2_3nf_ticket_rollback.sql thất bại." }
        python "$PSScriptRoot\check_migration.py" --db $targetDb --phase pre --maintenance
        if ($LASTEXITCODE -ne 0) { throw "Lược đồ/toàn vẹn sau rollback không đạt." }
    }
    Write-Output "[MIGRATION COMPLETED] Đã hoàn tất '$Direction' an toàn trên 'cinema_chain'. Bản sao lưu dự phòng: $backupSql"
} catch {
    Write-Warning "Migration chưa hoàn tất. GIỮ ỨNG DỤNG ĐÓNG; DDL MySQL không rollback nguyên khối. Đối soát trạng thái và dùng bản sao lưu đã kiểm tra tại '$backupSql' để phục hồi có kiểm soát."
    throw
} finally {
    Remove-Item -LiteralPath $cfgFile -Force -ErrorAction SilentlyContinue
}
