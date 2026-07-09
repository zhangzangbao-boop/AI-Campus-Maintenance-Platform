@echo off
echo ========================================
echo Campus Repair System - Frontend Service
echo ========================================
echo.

echo Starting frontend development server...
echo Frontend URL: http://localhost:3000
echo API Proxy: http://localhost:8080/api
echo.

REM Start Vite development server
cd /d %~dp0
call npm run dev

if errorlevel 1 (
    echo.
    echo Startup failed! Please check error messages.
    echo Possible causes:
    echo 1. npm dependencies not installed (run npm install first)
    echo 2. Port 3000 is already in use
    echo.
    pause
)