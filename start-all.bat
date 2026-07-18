@echo off
echo ========================================
echo AI Campus Maintenance Platform
echo ========================================
echo.

set "BACKEND_SCRIPT=%~dp0smart-backend\start-services.ps1"
set "FRONTEND_DIR=%~dp0smart-frontend"

if not exist "%BACKEND_SCRIPT%" (
    echo Error: smart-backend startup script was not found.
    echo Expected: %BACKEND_SCRIPT%
    pause
    exit /b 1
)

if not exist "%FRONTEND_DIR%\package.json" (
    echo Error: smart-frontend directory was not found.
    echo Expected: %FRONTEND_DIR%
    pause
    exit /b 1
)

echo Step 1: Opening smart-backend service launcher...
start "Smart Backend Services" powershell -NoExit -ExecutionPolicy Bypass -File "%BACKEND_SCRIPT%"
echo Gateway URL: http://localhost:8070
echo.

echo Step 2: Opening smart-frontend dev server...
start "Smart Frontend" cmd /k "cd /d ""%FRONTEND_DIR%"" && call npm run dev"
echo Frontend URL: http://localhost:5173
echo.

echo Access URLs:
echo 1. Frontend: http://localhost:5173
echo 2. Gateway:  http://localhost:8070
echo.
pause
