$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$mysqlDir        = Join-Path $env:USERPROFILE ".mysql\mysql-8.4"
$expectedDataDir = (Join-Path $env:USERPROFILE ".mysql\data-8.4").Replace('\', '/').TrimEnd('/')
$mysqlExe        = Join-Path $mysqlDir "bin\mysql.exe"
$mysqlAdminExe   = Join-Path $mysqlDir "bin\mysqladmin.exe"

$client = [System.Net.Sockets.TcpClient]::new()
$isListening = $false
try {
    $async = $client.BeginConnect("127.0.0.1", 3306, $null, $null)
    if ($async.AsyncWaitHandle.WaitOne(800, $false) -and $client.Connected) {
        $client.EndConnect($async)
        $isListening = $true
    }
} catch {
    $isListening = $false
} finally {
    $client.Dispose()
}

if (-not $isListening) {
    Write-Output "[INFO] Cổng 127.0.0.1:3306 hiện không có tiến trình MySQL nào đang chạy."
    exit 0
}

$rootPwd = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }
$oldPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = $rootPwd
    $info = (& $mysqlExe --protocol=TCP -h 127.0.0.1 -P 3306 -u root -N -s -e "SELECT CONCAT(VERSION(), '|', REPLACE(@@datadir, '\\', '/'));") -join ""
    $parts = $info.Split("|")
    $ver = $parts[0]
    $liveDataDir = $parts[1].TrimEnd('/')

    if ($liveDataDir.ToLower() -ne $expectedDataDir.ToLower()) {
        throw "CHẶN DỪNG NHẦM INSTANCE: Cổng 3306 đang thuộc datadir '$liveDataDir', không phải '$expectedDataDir'."
    }

    Write-Output "Đang dừng an toàn (graceful InnoDB shutdown) instance MySQL $ver tại $liveDataDir..."
    & $mysqlAdminExe --protocol=TCP -h 127.0.0.1 -P 3306 -u root shutdown
    if ($LASTEXITCODE -ne 0) {
        throw "Lệnh mysqladmin shutdown thất bại với mã thoát $LASTEXITCODE."
    }
} finally {
    $env:MYSQL_PWD = $oldPwd
}

Write-Output "[OK] Đã dừng an toàn instance MySQL 8.4 LTS (bảo toàn 100% dữ liệu InnoDB)."
