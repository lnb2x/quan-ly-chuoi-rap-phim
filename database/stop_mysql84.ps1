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

function New-RestrictedMySqlDefaultsFile([string]$user, [string]$pass) {
    $tmpPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cinema_mysql_" + [System.Guid]::NewGuid().ToString("N") + ".cnf")
    $escaped = $pass.Replace('\', '\\').Replace('"', '\"')
    $cfgContent = "[client]`nhost=127.0.0.1`nport=3306`nprotocol=TCP`nuser=$user`npassword=`"$escaped`"`n"
    [System.IO.File]::WriteAllText($tmpPath, $cfgContent, [System.Text.UTF8Encoding]::new($false))
    if ($env:USERNAME) {
        & icacls $tmpPath /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    }
    return $tmpPath
}

$rootPwd = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }
$cfgFile = New-RestrictedMySqlDefaultsFile "root" $rootPwd
try {
    $query = "SELECT CONCAT(VERSION(), '|', REPLACE(@@datadir, '\\', '/'));"
    $info = ($query | & $mysqlExe "--defaults-extra-file=$cfgFile" -N -s) -join ""
    $parts = $info.Split("|")
    $ver = $parts[0]
    $liveDataDir = $parts[1].TrimEnd('/')

    if ($liveDataDir.ToLower() -ne $expectedDataDir.ToLower()) {
        throw "CHẶN DỪNG NHẦM INSTANCE: Cổng 3306 đang thuộc datadir '$liveDataDir', không phải '$expectedDataDir'."
    }

    Write-Output "Đang dừng an toàn (graceful InnoDB shutdown) instance MySQL $ver tại $liveDataDir..."
    & $mysqlAdminExe "--defaults-extra-file=$cfgFile" shutdown
    if ($LASTEXITCODE -ne 0) {
        throw "Lệnh mysqladmin shutdown thất bại với mã thoát $LASTEXITCODE."
    }
} finally {
    Remove-Item -LiteralPath $cfgFile -Force -ErrorAction SilentlyContinue
}

Write-Output "[OK] Đã dừng an toàn instance MySQL 8.4 LTS (bảo toàn 100% dữ liệu InnoDB)."
