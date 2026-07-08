@echo off
echo ========================================
echo Campus Repair System - Starting All Services
echo ========================================
echo.

REM Start Backend (new window)
echo Step 1: Starting Backend Service...
start "Backend Service" cmd /k "cd /d %~dp0backend && call start-backend.bat"
echo Backend started in new window
echo Backend URL: http://localhost:8080
echo.

REM Wait 3 seconds
echo Waiting for backend initialization...
timeout /t 3 /nobreak >nul

REM Start Frontend (new window)
echo Step 2: Starting Frontend Service...
start "Frontend Service" cmd /k "cd /d %~dp0frontend && call start-frontend.bat"
echo Frontend started in new window
echo Frontend URL: http://localhost:3000
echo.

echo ========================================
echo Startup Complete!
echo ========================================
echo.
echo Access URLs:
echo 1. Frontend: http://localhost:3000
echo 2. Backend: http://localhost:8080
echo.
echo Tip: Services are running in separate windows
echo Close the windows to stop the services
echo.

pause