$ErrorActionPreference = "Stop"
[Console]::InputEncoding  = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$rootDir = Split-Path -Parent $PSScriptRoot
Push-Location $rootDir

function Invoke-TestDbSqlStdin {
    param(
        [Parameter(Mandatory = $true)][string]$DatabaseName,
        [Parameter(Mandatory = $true)][string]$SqlScriptPath,
        [Parameter(Mandatory = $true)][string]$LogOutputPath
    )
    $mysqlExe = Join-Path $env:USERPROFILE ".mysql\mysql-8.4\bin\mysql.exe"
    $appPropsPath = Join-Path $rootDir "config\application.properties"
    $appPwd = if ($env:CINEMA_TEST_DB_PASSWORD) { $env:CINEMA_TEST_DB_PASSWORD } elseif ($env:CINEMA_DB_PASSWORD) { $env:CINEMA_DB_PASSWORD } else { "" }
    $appUser = "cinema_app"
    if ((-not $appPwd) -and (Test-Path $appPropsPath)) {
        foreach ($line in Get-Content $appPropsPath -Encoding UTF8) {
            if ($line -match '^\s*test\.db\.password\s*=\s*(.+)$') { $appPwd = $Matches[1].Trim() }
            elseif ((-not $appPwd) -and ($line -match '^\s*db\.password\s*=\s*(.+)$')) { $appPwd = $Matches[1].Trim() }
            if ($line -match '^\s*test\.db\.username\s*=\s*(.+)$') { $appUser = $Matches[1].Trim() }
        }
    }
    $tmpDefaults = Join-Path ([System.IO.Path]::GetTempPath()) ("mysql_verify_" + [guid]::NewGuid().ToString("N") + ".cnf")
    $escapedPwd = $appPwd.Replace('\', '\\').Replace('"', '\"')
    $cnfContent = "[client]`r`nhost=127.0.0.1`r`nport=3306`r`nprotocol=TCP`r`ndefault-character-set=utf8mb4`r`nuser=$appUser`r`npassword=`"$escapedPwd`"`r`n"
    [System.IO.File]::WriteAllText($tmpDefaults, $cnfContent, (New-Object System.Text.UTF8Encoding($false)))
    & icacls $tmpDefaults /inheritance:r /grant:r "$($env:USERNAME):(R,W)" | Out-Null
    try {
        $sqlText = Get-Content -LiteralPath $SqlScriptPath -Raw -Encoding UTF8
        $prevPref = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $out = $sqlText | & $mysqlExe "--defaults-extra-file=$tmpDefaults" --table $DatabaseName 2>&1
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = $prevPref
        $out | Out-File -FilePath $LogOutputPath -Encoding utf8
        if ($exitCode -ne 0) {
            throw "Thực thi $SqlScriptPath trên $DatabaseName thất bại (ExitCode=$exitCode)."
        }
    } finally {
        if (Test-Path $tmpDefaults) {
            Remove-Item -LiteralPath $tmpDefaults -Force -ErrorAction SilentlyContinue
        }
    }
}

try {
    New-Item -ItemType Directory -Force -Path "verification\logs" | Out-Null
    New-Item -ItemType Directory -Force -Path "verification\test-results" | Out-Null

    Write-Output "======================================================================"
    Write-Output " [1/6] XÁC MINH MYSQL 8.4.0 LTS & DẤU VÂN TAY SHA-256 BAN ĐẦU CỦA 'cinema_chain'"
    Write-Output "       (CHỈ ĐỌC — TUYỆT ĐỐI KHÔNG GỌI setup.ps1 HAY SỬA SCHEMA/TÀI KHOẢN)"
    Write-Output "======================================================================"
    powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\start_mysql84.ps1"
    if ($LASTEXITCODE -ne 0) { throw "start_mysql84.ps1 thất bại." }

    $baselineFp = "verification\logs\cinema_chain_baseline_fingerprint.json"
    if (-not (Test-Path $baselineFp)) {
        python tools\db_fingerprint.py --db cinema_chain --file $baselineFp --mode capture --use-root
        if ($LASTEXITCODE -ne 0) { throw "Không thể chụp dấu vân tay ban đầu cho cinema_chain." }
    } else {
        python tools\db_fingerprint.py --db cinema_chain --file $baselineFp --mode verify --use-root
        if ($LASTEXITCODE -ne 0) { throw "CSDL demo cinema_chain đã khác với dấu vân tay mốc ban đầu trước khi chạy kiểm thử!" }
    }

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [2/6] XÓA XML CŨ, CHẠY MAVEN CLEAN VERIFY (-Pmysql-it) & KIỂM TOÁN XML MỚI"
    Write-Output "======================================================================"
    Remove-Item -Path "verification\test-results\TEST-*.xml" -Force -ErrorAction SilentlyContinue
    $runStart = (Get-Date).AddSeconds(-2)

    $ErrorActionPreference = "Continue"
    $mvnOut = & .\mvnw.cmd clean verify -Pmysql-it 2>&1
    $mvnExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    $mvnOut | Out-File -FilePath "verification\logs\mvn_clean_verify.log" -Encoding utf8
    if ($mvnExit -ne 0) {
        $mvnOut | Select-Object -Last 50 | Write-Output
        throw "mvnw.cmd clean verify -Pmysql-it thất bại (ExitCode=$mvnExit)."
    }
    $mvnOut | Select-String "Tests run:|BUILD SUCCESS" | ForEach-Object { Write-Output $_.Line }

    $freshXmlFiles = @()
    if (Test-Path "target\surefire-reports") {
        $freshXmlFiles += Get-ChildItem "target\surefire-reports\TEST-*.xml"
    }
    if (Test-Path "target\failsafe-reports") {
        $freshXmlFiles += Get-ChildItem "target\failsafe-reports\TEST-*.xml"
    }
    if ($freshXmlFiles.Count -eq 0) {
        throw "Không tìm thấy bất kỳ tệp báo cáo TEST-*.xml mới nào trong target/surefire-reports hoặc target/failsafe-reports!"
    }

    $requiredSuites = @(
        "com.cinema.unit.ScreeningValidationTest",
        "com.cinema.unit.TestSafetyGuardTest",
        "com.cinema.architecture.ArchitectureComplianceTest",
        "com.cinema.integration.DatabaseIntegrityIT",
        "com.cinema.integration.ScreeningConcurrencyIT",
        "com.cinema.integration.BookingConcurrencyIT",
        "com.cinema.integration.ScreeningSearchIT",
        "com.cinema.integration.ReportAccuracyIT"
    )
    $observedSuites = @{}
    $totalRun = 0; $totalFail = 0; $totalErr = 0; $totalSkip = 0

    foreach ($xmlFile in $freshXmlFiles) {
        if ($xmlFile.LastWriteTime -lt $runStart) {
            throw "Phát hiện tệp báo cáo XML cũ không thuộc lần chạy hiện tại: $($xmlFile.FullName) ($($xmlFile.LastWriteTime) < $runStart)"
        }
        [xml]$doc = Get-Content $xmlFile.FullName -Encoding UTF8
        $ts = $doc.testsuite
        $suiteName = [string]$ts.name
        $observedSuites[$suiteName] = [int]$ts.tests
        $totalRun  += [int]$ts.tests
        $totalFail += [int]$ts.failures
        $totalErr  += [int]$ts.errors
        $totalSkip += [int]$ts.skipped
        Copy-Item $xmlFile.FullName "verification\test-results\" -Force
    }

    foreach ($req in $requiredSuites) {
        if (-not $observedSuites.ContainsKey($req) -or $observedSuites[$req] -le 0) {
            throw "Thiếu lớp kiểm thử bắt buộc hoặc số test = 0 trong lần chạy hiện tại: $req"
        }
    }

    Write-Output "[JUNIT FRESH XML AUDIT] RunTimestamp=$($runStart.ToString('yyyy-MM-dd HH:mm:ss')) | Suites=$($observedSuites.Count) | TotalTests=$totalRun | Failures=$totalFail | Errors=$totalErr | Skipped=$totalSkip"
    if ($totalRun -le 0 -or $totalFail -ne 0 -or $totalErr -ne 0 -or $totalSkip -ne 0) {
        throw "Kiểm toán JUnit XML không đạt (Failures/Errors/Skipped phải bằng 0)!"
    }

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [3/6] CHẠY KIỂM CHỨNG ĐIỀU KHIỂN 5 MÀN HÌNH SWING & XUẤT 2 BÁO CÁO PDF"
    Write-Output "======================================================================"
    $ErrorActionPreference = "Continue"
    $guiOut = & .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.gui.GuiVerificationRunner" "-Dexec.classpathScope=test" 2>&1
    $guiExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    $guiOut | Out-File -FilePath "verification\logs\gui_verification_test_db.log" -Encoding utf8
    if ($guiExit -ne 0) {
        $guiOut | Select-Object -Last 40 | Write-Output
        throw "GuiVerificationRunner thất bại (ExitCode=$guiExit)."
    }
    $guiOut | Select-String "APP-STARTUP|GUI-VERIFY|GUI-ASSERT-PASS|completed successfully" | ForEach-Object { Write-Output $_.Line }

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [4/6] KIỂM CHỨNG ĐÓNG & MỞ LẠI ỨNG DỤNG QUA 2 TIẾN TRÌNH JVM TÁCH BIỆT"
    Write-Output "======================================================================"
    $ErrorActionPreference = "Continue"
    $persistOut1 = & .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.gui.AppPersistenceVerificationRunner" "-Dexec.classpathScope=test" "-Dexec.args=--session1 target/persistence-session-state.properties" 2>&1
    $persistExit1 = $LASTEXITCODE
    if ($persistExit1 -ne 0) {
        $ErrorActionPreference = "Stop"
        $persistOut1 | Out-File -FilePath "verification\logs\persistence_restart_verify.log" -Encoding utf8
        $persistOut1 | Select-Object -Last 40 | Write-Output
        throw "AppPersistenceVerificationRunner --session1 thất bại (ExitCode=$persistExit1)."
    }
    $persistOut2 = & .\mvnw.cmd exec:java "-Dexec.mainClass=com.cinema.gui.AppPersistenceVerificationRunner" "-Dexec.classpathScope=test" "-Dexec.args=--session2 target/persistence-session-state.properties" 2>&1
    $persistExit2 = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    ($persistOut1 + $persistOut2) | Out-File -FilePath "verification\logs\persistence_restart_verify.log" -Encoding utf8
    if ($persistExit2 -ne 0) {
        $persistOut2 | Select-Object -Last 40 | Write-Output
        throw "AppPersistenceVerificationRunner --session2 thất bại (ExitCode=$persistExit2)."
    }
    ($persistOut1 + $persistOut2) | Select-String "APP-STARTUP|BEFORE-CLOSE|AFTER-REOPEN|PERSISTENCE-VERIFIED" | ForEach-Object { Write-Output $_.Line }

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [5/6] THỰC THI BỘ TRUY VẤN SQL CHỨC NĂNG R5-R7 TRÊN 'cinema_chain_test'"
    Write-Output "======================================================================"
    $funcSqlPath = Join-Path $PSScriptRoot "queries\functional_sql_verification.sql"
    if (Test-Path $funcSqlPath) {
        Invoke-TestDbSqlStdin -DatabaseName "cinema_chain_test" -SqlScriptPath $funcSqlPath -LogOutputPath "verification\logs\functional_sql_examples_test_db.log"
        Write-Output "[FUNCTIONAL SQL VERIFIED] Log saved to verification/logs/functional_sql_examples_test_db.log"
    } else {
        Write-Output "[INFO] Chưa có functional_sql_verification.sql — bỏ qua bước 5."
    }

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [6/6] ĐỐI CHỨNG DẤU VÂN TAY SHA-256 ĐẦY ĐỦ CỦA CSDL DEMO 'cinema_chain'"
    Write-Output "======================================================================"
    python tools\db_fingerprint.py --db cinema_chain --file $baselineFp --mode verify --use-root
    if ($LASTEXITCODE -ne 0) {
        throw "LỖI BẢO TOÀN DỮ LIỆU DEMO: Dấu vân tay SHA-256 của cinema_chain bị sai lệch sau khi chạy kiểm chứng!"
    }
    Write-Output "[ALL VERIFICATION GATES PASSED] Đã xác minh trên MySQL 8.4.0 LTS thật; CSDL demo cinema_chain bảo toàn nguyên vẹn (SHA-256 khớp 100% phạm vi kiểm tra)."
} finally {
    Pop-Location
}
