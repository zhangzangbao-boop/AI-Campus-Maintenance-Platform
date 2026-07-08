@echo off
echo ========================================
echo Campus Repair System - Clean and Build
echo ========================================
echo.

REM Set Java 21 environment
set JAVA_HOME=D:\jdk21
set Path=%JAVA_HOME%\bin;%Path%

echo Using Java version:
java -version
echo.

cd /d %~dp0

echo Step 1: Cleaning previous build...
echo Deleting target directory...
if exist target (
    rmdir /s /q target
    echo Target directory deleted successfully
) else (
    echo Target directory not found
)
echo.

echo Step 2: Compiling project...
call mvn clean compile

if errorlevel 1 (
    echo.
    echo Build failed! Please check error messages.
    pause
) else (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.
    echo Now you can run start-backend.bat to start the service
    echo.
    pause
)