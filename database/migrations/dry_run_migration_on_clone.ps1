$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$rootDir  = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dbDir    = Split-Path -Parent $PSScriptRoot
$mysqlBin = Join-Path $env:USERPROFILE ".mysql\mysql-8.4\bin"
$mysqlExe = Join-Path $mysqlBin "mysql.exe"
$dumpExe  = Join-Path $mysqlBin "mysqldump.exe"
$cloneDb  = "cinema_chain_migration_clone"
$logFile  = Join-Path $rootDir "verification\logs\migration_clone_dryrun.log"

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logFile) | Out-Null

function New-RestrictedMySqlDefaultsFile([string]$user, [string]$pass) {
    $tmpPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cinema_mig_" + [System.Guid]::NewGuid().ToString("N") + ".cnf")
    $escaped = $pass.Replace('\', '\\').Replace('"', '\"')
    $cfgContent = "[client]`nhost=127.0.0.1`nport=3306`nprotocol=TCP`nuser=$user`npassword=`"$escaped`"`n[mysql]`ndefault-character-set=utf8mb4`n[mysqldump]`ndefault-character-set=utf8mb4`n"
    [System.IO.File]::WriteAllText($tmpPath, $cfgContent, [System.Text.UTF8Encoding]::new($false))
    if ($env:USERNAME) {
        & icacls $tmpPath /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    }
    return $tmpPath
}

$rootPass = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }
$cfgFile  = New-RestrictedMySqlDefaultsFile "root" $rootPass

"=== REHEARSAL: NON-DESTRUCTIVE 3NF MIGRATION & ROLLBACK ON CLONE '$cloneDb' ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')) ===" | Out-File -FilePath $logFile -Encoding utf8

try {
    Write-Output "[1/7] Sao chép nguyên trạng 'cinema_chain' sang bản sao '$cloneDb' (KHÔNG chạm dữ liệu gốc)..."
    ("DROP DATABASE IF EXISTS $cloneDb; CREATE DATABASE $cloneDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;") | & $mysqlExe "--defaults-extra-file=$cfgFile"
    if ($LASTEXITCODE -ne 0) { throw "Không thể tạo database clone '$cloneDb'." }

    $dumpContent = & $dumpExe "--defaults-extra-file=$cfgFile" --single-transaction --triggers --routines --no-tablespaces cinema_chain
    if ($LASTEXITCODE -ne 0) { throw "mysqldump từ 'cinema_chain' thất bại." }
    ($dumpContent -join "`n") | & $mysqlExe "--defaults-extra-file=$cfgFile" $cloneDb
    if ($LASTEXITCODE -ne 0) { throw "Nạp bản sao vào '$cloneDb' thất bại." }

    $cloneBeforeJson = Join-Path $rootDir "target\migration_clone_before.json"
    python "$rootDir\tools\db_fingerprint.py" --db $cloneDb --file $cloneBeforeJson --mode capture --use-root | Tee-Object -FilePath $logFile -Append

    $preJoinSha = (("SELECT tk.ticket_id, tk.booking_id, bk.screening_id, tk.seat_id, tk.unit_price, tk.ticket_status, COALESCE(DATE_FORMAT(tk.cancelled_at, '%Y-%m-%d %H:%i:%s'), 'NULL') FROM ticket tk JOIN booking bk ON bk.booking_id = tk.booking_id ORDER BY tk.ticket_id ASC;" | & $mysqlExe "--defaults-extra-file=$cfgFile" -N -B $cloneDb) -join "`n")
    $preJoinHash = [System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($preJoinSha))).Replace("-", "").ToLower()
    "[PRE-MIGRATION LOSSLESS JOIN SHA256] $preJoinHash" | Tee-Object -FilePath $logFile -Append

    Write-Output "[2/7] Chạy kiểm tra điều kiện tiên quyết (V2_3nf_ticket_precheck.sql)..."
    "--- STEP 2: PRECHECK OUTPUT ---" | Out-File -FilePath $logFile -Append -Encoding utf8
    [System.IO.File]::ReadAllText("$PSScriptRoot\V2_3nf_ticket_precheck.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" -t $cloneDb | Out-File -FilePath $logFile -Append -Encoding utf8
    if ($LASTEXITCODE -ne 0) { throw "Precheck thất bại." }

    Write-Output "[3/7] Chạy Forward Migration tại chỗ sang 14 bảng chuẩn 3NF (V2_3nf_ticket_up.sql + 02_triggers.sql + 03_views.sql)..."
    [System.IO.File]::ReadAllText("$PSScriptRoot\V2_3nf_ticket_up.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $cloneDb
    if ($LASTEXITCODE -ne 0) { throw "V2_3nf_ticket_up.sql thất bại." }
    [System.IO.File]::ReadAllText("$dbDir\schema\02_triggers.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $cloneDb
    if ($LASTEXITCODE -ne 0) { throw "Cập nhật 02_triggers.sql trên clone thất bại." }
    [System.IO.File]::ReadAllText("$dbDir\schema\03_views.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $cloneDb
    if ($LASTEXITCODE -ne 0) { throw "Cập nhật 03_views.sql trên clone thất bại." }

    Write-Output "[4/7] Đối soát bảo toàn dữ liệu không mất mát (Lossless-Join Reconciliation) sau Forward Migration..."
    "--- STEP 4: POST-MIGRATION VERIFY CONSTRAINTS ---" | Out-File -FilePath $logFile -Append -Encoding utf8
    $postVerify = [System.IO.File]::ReadAllText("$dbDir\verification\verify_constraints.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" -t $cloneDb
    if ($LASTEXITCODE -ne 0) { throw "verify_constraints.sql sau migration thất bại." }
    $postVerify | Out-File -FilePath $logFile -Append -Encoding utf8

    $postJoinSha = (("SELECT tk.ticket_id, tk.booking_id, bk.screening_id, tk.seat_id, tk.unit_price, tk.ticket_status, COALESCE(DATE_FORMAT(tk.cancelled_at, '%Y-%m-%d %H:%i:%s'), 'NULL') FROM ticket tk JOIN booking bk ON bk.booking_id = tk.booking_id ORDER BY tk.ticket_id ASC;" | & $mysqlExe "--defaults-extra-file=$cfgFile" -N -B $cloneDb) -join "`n")
    $postJoinHash = [System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash([System.Text.Encoding]::UTF8.GetBytes($postJoinSha))).Replace("-", "").ToLower()
    "[POST-MIGRATION LOSSLESS JOIN SHA256] $postJoinHash" | Tee-Object -FilePath $logFile -Append
    if ($preJoinHash -ne $postJoinHash) {
        throw "LỖI BẢO TOÀN DỮ LIỆU: Kết quả kết nối tự nhiên ticket ⋈ booking thay đổi sau migration!"
    }

    Write-Output "[5/7] Chạy diễn tập Khôi phục tại chỗ (V2_3nf_ticket_rollback.sql) về lại 13 bảng..."
    [System.IO.File]::ReadAllText("$PSScriptRoot\V2_3nf_ticket_rollback.sql", [System.Text.Encoding]::UTF8) | & $mysqlExe "--defaults-extra-file=$cfgFile" $cloneDb
    if ($LASTEXITCODE -ne 0) { throw "V2_3nf_ticket_rollback.sql thất bại." }

    Write-Output "[6/7] Đối chứng toàn bộ dữ liệu 13 bảng sau Rollback với trạng thái gốc ban đầu..."
    $cloneAfterRollbackJson = Join-Path $rootDir "target\migration_clone_after_rollback.json"
    python "$rootDir\tools\db_fingerprint.py" --db $cloneDb --file $cloneAfterRollbackJson --mode capture --use-root | Out-Null
    $beforeObj = Get-Content $cloneBeforeJson -Raw -Encoding UTF8 | ConvertFrom-Json
    $afterObj  = Get-Content $cloneAfterRollbackJson -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($prop in $beforeObj.tables.PSObject.Properties) {
        $tblName = $prop.Name
        $bData = $prop.Value.data_sha256
        $aData = $afterObj.tables.$tblName.data_sha256
        if ($bData -ne $aData) {
            throw "LỖI KHÔI PHỤC DỮ LIỆU BẢNG '$tblName': data_sha256 lệch ($bData vs $aData)!"
        }
    }
    "[ROLLBACK DATA RECONCILIATION] Tất cả 13/13 bảng đều có data_sha256 KHỚP TUYỆT ĐỐI 100% sau Rollback!" | Tee-Object -FilePath $logFile -Append

    Write-Output "[7/7] Dọn dẹp bản sao '$cloneDb' và đối chứng CSDL chính 'cinema_chain' bất biến..."
    ("DROP DATABASE IF EXISTS $cloneDb;") | & $mysqlExe "--defaults-extra-file=$cfgFile"
    python "$rootDir\tools\db_fingerprint.py" --db cinema_chain --file "$rootDir\verification\logs\cinema_chain_baseline_fingerprint.json" --mode verify --use-root | Tee-Object -FilePath $logFile -Append
    if ($LASTEXITCODE -ne 0) { throw "CSDL chính 'cinema_chain' bị ảnh hưởng!" }

    Write-Output "[DRY-RUN MIGRATION SUCCESS] Diễn tập Forward Migration + Đối soát Lossless-Join + Rollback trên bản sao hoàn tất 100%. Nhật ký: $logFile"
} finally {
    ("DROP DATABASE IF EXISTS $cloneDb;") | & $mysqlExe "--defaults-extra-file=$cfgFile" 2>$null
    Remove-Item -LiteralPath $cfgFile -Force -ErrorAction SilentlyContinue
}
