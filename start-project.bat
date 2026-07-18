@echo off
chcp 65001 >nul

set "ROOT=%~dp0"
set "LAUNCHER=%ROOT%start-project.ps1"

if not exist "%LAUNCHER%" (
    echo [ERROR] start-project.ps1 was not found.
    echo Expected: %LAUNCHER%
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%LAUNCHER%"

echo.
echo Press any key to close this launcher window.
pause >nul
