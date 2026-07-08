@echo off
echo ========================================
echo Campus Repair System - Build and Run
echo ========================================
echo.

REM Set Java 21 environment
set JAVA_HOME=D:\jdk21
set Path=%JAVA_HOME%\bin;%Path%

echo Using Java version:
java -version
echo.

cd /d %~dp0

REM Clean previous build
echo Step 1: Cleaning previous build...
if exist target (
    rmdir /s /q target
    echo Target directory cleaned
)
echo.

REM Compile and package
echo Step 2: Building project (this may take a few minutes)...
call mvn clean package -DskipTests

if errorlevel 1 (
    echo.
    echo ========================================
    echo Build failed!
    echo ========================================
    echo.
    pause
) else (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.

    REM Check if jar file exists
    if exist target\repairing-center-0.0.1-SNAPSHOT.jar (
        echo Starting application from jar file...
        echo Backend URL: http://localhost:8080
        echo.
        java -jar target\repairing-center-0.0.1-SNAPSHOT.jar
    ) else (
        echo.
        echo ERROR: Jar file not found!
        echo Expected: target\repairing-center-0.0.1-SNAPSHOT.jar
        echo.
        pause
    )
)