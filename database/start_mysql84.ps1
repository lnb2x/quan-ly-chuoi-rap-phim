$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$mysqlDir        = Join-Path $env:USERPROFILE ".mysql\mysql-8.4"
$expectedDataDir = (Join-Path $env:USERPROFILE ".mysql\data-8.4").Replace('\', '/').TrimEnd('/')
$mysqldExe       = Join-Path $mysqlDir "bin\mysqld.exe"
$mysqlExe        = Join-Path $mysqlDir "bin\mysql.exe"
$myIni           = Join-Path $mysqlDir "my.ini"

if (-not (Test-Path $mysqldExe) -or -not (Test-Path $myIni)) {
    throw "Không tìm thấy bản cài đặt MySQL 8.4 LTS chuẩn tại $mysqlDir hoặc thiếu tệp $myIni."
}

function Test-Port3306Open {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect("127.0.0.1", 3306, $null, $null)
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

function New-RestrictedMySqlDefaultsFile([string]$user, [string]$pass) {
    $tmpPath = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "cinema_mysql_" + [System.Guid]::NewGuid().ToString("N") + ".cnf")
    $escaped = $pass.Replace('\', '\\').Replace('"', '\"')
    $cfgContent = "[client]`nhost=127.0.0.1`nport=3306`nprotocol=TCP`nuser=$user`npassword=`"$escaped`"`n[mysql]`ndefault-character-set=utf8mb4`n"
    [System.IO.File]::WriteAllText($tmpPath, $cfgContent, [System.Text.UTF8Encoding]::new($false))
    if ($env:USERNAME) {
        & icacls $tmpPath /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    }
    return $tmpPath
}

if (-not (Test-Port3306Open)) {
    Write-Output "Đang khởi động MySQL Community Server 8.4.0 LTS từ: $mysqldExe (--defaults-file=$myIni)..."
    Start-Process -FilePath $mysqldExe -ArgumentList "--defaults-file=$myIni" -WindowStyle Hidden
    for ($i = 0; $i -lt 15; $i++) {
        Start-Sleep -Milliseconds 700
        if (Test-Port3306Open) { break }
    }
}

if (-not (Test-Port3306Open)) {
    throw "MySQL 8.4 LTS chưa phản hồi trên 127.0.0.1:3306. Kiểm tra nhật ký lỗi tại $expectedDataDir\*.err."
}

$rootPwd = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }
$cfgFile = New-RestrictedMySqlDefaultsFile "root" $rootPwd
try {
    $query = "SELECT CONCAT(VERSION(), '|', @@version_comment, '|', REPLACE(@@datadir, '\\', '/'));"
    $info = ($query | & $mysqlExe "--defaults-extra-file=$cfgFile" -N -s) -join ""
    if ($LASTEXITCODE -ne 0) {
        throw "Không thể kiểm tra phiên bản MySQL trên 127.0.0.1:3306 (ExitCode=$LASTEXITCODE)."
    }
} finally {
    Remove-Item -LiteralPath $cfgFile -Force -ErrorAction SilentlyContinue
}

$parts = $info.Split("|")
$ver = $parts[0]
$comment = $parts[1]
$liveDataDir = $parts[2].TrimEnd('/')

if ($ver -notlike "8.4.*" -or $liveDataDir.ToLower() -ne $expectedDataDir.ToLower()) {
    throw "CẢNH BÁO SAI INSTANCE: Cổng 3306 đang chạy '$ver ($comment)' với datadir='$liveDataDir' thay vì '$expectedDataDir'!"
}

Write-Output "[OK] Đúng instance MySQL 8.4 LTS đang hoạt động: Version=$ver | Distribution=$comment | DataDir=$liveDataDir | Endpoint=127.0.0.1:3306"
