@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
if not exist "runtime\cinema-chain-management-1.0.0.jar" (
    echo Chưa có chương trình runtime. Hãy giải nén toàn bộ gói nộp hoặc chạy mvnw.cmd từ source.
    exit /b 2
)
if not exist "config\application.properties" (
    echo Chưa cấu hình MySQL. Sao chép config\application.properties.example thành config\application.properties và điền mật khẩu cục bộ.
    exit /b 2
)
set "CINEMA_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "CINEMA_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "%USERPROFILE%\.jdks\temurin-21\bin\java.exe" set "CINEMA_JAVA=%USERPROFILE%\.jdks\temurin-21\bin\java.exe"
"%CINEMA_JAVA%" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "runtime\cinema-chain-management-1.0.0.jar;runtime\lib\*" com.cinema.app.Main
exit /b %ERRORLEVEL%
