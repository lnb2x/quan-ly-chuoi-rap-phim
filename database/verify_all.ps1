$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$rootDir = Split-Path -Parent $PSScriptRoot
Push-Location $rootDir

try {
    Write-Output "======================================================================"
    Write-Output " [1/5] XÁC MINH INSTANCE MYSQL 8.4.0 LTS & ĐỒNG BỘ NORMAL SETUP AN TOÀN"
    Write-Output "======================================================================"
    powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\start_mysql84.ps1"
    if ($LASTEXITCODE -ne 0) { throw "start_mysql84.ps1 thất bại." }

    powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\setup.ps1"
    if ($LASTEXITCODE -ne 0) { throw "setup.ps1 (Normal Setup) thất bại." }

    # Ghi nhận trạng thái bất biến của cinema_chain trước khi chạy toàn bộ kiểm thử
    $mysqlExe = Join-Path $env:USERPROFILE ".mysql\mysql-8.4\bin\mysql.exe"
    $rootPwd = if ($env:MYSQL_ROOT_PASSWORD) { $env:MYSQL_ROOT_PASSWORD } else { "" }
    $oldPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $rootPwd
        $demoBefore = (& $mysqlExe --protocol=TCP -h 127.0.0.1 -P 3306 -u root -N -s cinema_chain -e "SELECT CONCAT((SELECT COUNT(*) FROM screening), ',', (SELECT COUNT(*) FROM booking), ',', (SELECT COUNT(*) FROM ticket), ',', (SELECT COUNT(*) FROM payment), ',', (SELECT COALESCE(SUM(total_paid_revenue),0) FROM vw_branch_daily_revenue));").Trim()
    } finally {
        $env:MYSQL_PWD = $oldPwd
    }
    Write-Output "[DEMO-DB SNAPSHOT BEFORE TESTS] cinema_chain = $demoBefore"

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [2/5] CHẠY MAVEN CLEAN VERIFY (-Pmysql-it) & KIỂM TRA 0 SKIPPED TESTS"
    Write-Output "======================================================================"
    $ErrorActionPreference = "Continue"
    $mvnOut = & .\mvnw.cmd clean verify -Pmysql-it 2>&1
    $mvnExit = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    $mvnOut | Out-File -FilePath "verification\logs\mvn_clean_verify.log" -Encoding utf8
    if ($mvnExit -ne 0) {
        $mvnOut | Select-Object -Last 50 | Write-Output
        throw "mvnw.cmd clean verify -Pmysql-it thất bại (ExitCode=$mvnExit)."
    }
    Copy-Item target\surefire-reports\TEST-*.xml verification\test-results\ -Force -ErrorAction SilentlyContinue
    Copy-Item target\failsafe-reports\TEST-*.xml verification\test-results\ -Force -ErrorAction SilentlyContinue
    $mvnOut | Select-String "Tests run:|BUILD SUCCESS" | ForEach-Object { Write-Output $_.Line }

    # Kiểm tra nghiêm ngặt tất cả XML báo cáo test: Failures=0, Errors=0, Skipped=0
    $totalRun = 0; $totalFail = 0; $totalErr = 0; $totalSkip = 0
    foreach ($xmlFile in Get-ChildItem "verification\test-results\TEST-*.xml") {
        [xml]$doc = Get-Content $xmlFile.FullName -Encoding UTF8
        $ts = $doc.testsuite
        $totalRun  += [int]$ts.tests
        $totalFail += [int]$ts.failures
        $totalErr  += [int]$ts.errors
        $totalSkip += [int]$ts.skipped
    }
    Write-Output "[JUNIT XML AUDIT] Total Tests=$totalRun | Failures=$totalFail | Errors=$totalErr | Skipped=$totalSkip"
    if ($totalRun -le 0 -or $totalFail -ne 0 -or $totalErr -ne 0 -or $totalSkip -ne 0) {
        throw "Kiểm toán JUnit XML không đạt (Test bị bỏ qua không được tính là PASS)!"
    }

    Write-Output ""
    Write-Output "======================================================================"
    Write-Output " [3/5] CHẠY KIỂM CHỨNG 5 MÀN HÌNH SWING THẬT & XUẤT 2 BÁO CÁO PDF"
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
    Write-Output " [4/5] CHẠY KIỂM CHỨNG BẢO TOÀN DỮ LIỆU QUA 2 TIẾN TRÌNH JVM (ĐÓNG & MỞ LẠI)"
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
    Write-Output " [5/5] ĐỐI CHỨNG BẢO TOÀN TUYỆT ĐỐI CSDL DEMO 'cinema_chain'"
    Write-Output "======================================================================"
    try {
        $env:MYSQL_PWD = $rootPwd
        $demoAfter = (& $mysqlExe --protocol=TCP -h 127.0.0.1 -P 3306 -u root -N -s cinema_chain -e "SELECT CONCAT((SELECT COUNT(*) FROM screening), ',', (SELECT COUNT(*) FROM booking), ',', (SELECT COUNT(*) FROM ticket), ',', (SELECT COUNT(*) FROM payment), ',', (SELECT COALESCE(SUM(total_paid_revenue),0) FROM vw_branch_daily_revenue));").Trim()
    } finally {
        $env:MYSQL_PWD = $oldPwd
    }
    Write-Output "[DEMO-DB SNAPSHOT AFTER TESTS]  cinema_chain = $demoAfter"
    if ($demoBefore -ne $demoAfter) {
        throw "LỖI BẢO TOÀN DỮ LIỆU DEMO: cinema_chain bị thay đổi ($demoBefore -> $demoAfter)!"
    }
    Write-Output "[ALL GATES PASSED] Môi trường, Toàn vẹn CSDL, Giao dịch đồng thời, 5 Màn hình GUI, 2 PDF và Bảo toàn dữ liệu đều ĐẠT 100%!"
} finally {
    Pop-Location
}
