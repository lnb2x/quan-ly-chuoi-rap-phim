@echo off
setlocal

if exist "%USERPROFILE%\.jdks\temurin-21\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\.jdks\temurin-21"
    set "PATH=%USERPROFILE%\.jdks\temurin-21\bin;%PATH%"
)

where mvn.cmd >nul 2>nul
if not errorlevel 1 goto :run_path_mvn

if exist "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" goto :run_netbeans_mvn

for /d %%D in ("%USERPROFILE%\.m2\wrapper\dists\apache-maven-*\*") do (
    if exist "%%D\bin\mvn.cmd" (
        set "MVN_BIN=%%D\bin\mvn.cmd"
        goto :run_wrapper_mvn
    )
)

echo [ERROR] Could not locate Maven executable.
exit /b 1

:run_path_mvn
call mvn.cmd %*
exit /b %ERRORLEVEL%

:run_netbeans_mvn
call "C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%

:run_wrapper_mvn
call "%MVN_BIN%" %*
exit /b %ERRORLEVEL%
