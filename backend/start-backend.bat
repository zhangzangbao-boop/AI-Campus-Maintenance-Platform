@echo off
echo ========================================
echo Campus Repair System - Backend Service
echo ========================================
echo.

REM Set Java 21 environment
set JAVA_HOME=D:\jdk21
set Path=%JAVA_HOME%\bin;%Path%

REM Display current Java version
echo Using Java version:
java -version
echo.
echo JAVA_HOME: %JAVA_HOME%
echo.

cd /d %~dp0

REM Check if target directory exists and has corrupted files
if exist target\maven-status (
    echo Found corrupted build status files
    echo Cleaning target directory first...
    rmdir /s /q target
    echo Target cleaned successfully
    echo.
)

echo Starting backend service...
echo Backend URL: http://localhost:8080
echo.

REM Start Spring Boot application
call mvn spring-boot:run

if errorlevel 1 (
    echo.
    echo ========================================
    echo Startup failed!
    echo ========================================
    echo.
    echo Possible causes:
    echo 1. Build files corrupted - Run clean-build.bat first
    echo 2. Database not started or connection error
    echo 3. Port 8080 is already in use
    echo 4. Maven dependencies not installed
    echo.
    echo Try running clean-build.bat to fix the issue
    echo.
    pause
)