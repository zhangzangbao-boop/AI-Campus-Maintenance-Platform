@echo off
echo ========================================
echo AI Campus Maintenance Platform Frontend
echo ========================================
echo.

echo Starting Vite development server...
echo Frontend URL: http://localhost:5173
echo API proxy: http://localhost:8070/api
echo.

cd /d %~dp0
call npm run dev

if errorlevel 1 (
    echo.
    echo Startup failed. Please check the error output above.
    echo Possible causes:
    echo 1. npm dependencies are not installed.
    echo 2. Port 5173 is already in use.
    echo.
    pause
)
