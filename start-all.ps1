# AI Campus Maintenance Platform - start smart backend and frontend

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AI Campus Maintenance Platform" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$backendPath = Join-Path $PSScriptRoot "smart-backend\start-services.ps1"
$frontendPath = Join-Path $PSScriptRoot "smart-frontend\start-frontend.ps1"

if (-not (Test-Path -LiteralPath $backendPath)) {
    Write-Host "Error: smart-backend startup script was not found." -ForegroundColor Red
    Write-Host "Expected: $backendPath" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

if (-not (Test-Path -LiteralPath $frontendPath)) {
    Write-Host "Error: smart-frontend startup script was not found." -ForegroundColor Red
    Write-Host "Expected: $frontendPath" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "Project structure check passed." -ForegroundColor Green
Write-Host ""

Write-Host "Step 1: Opening smart-backend service launcher..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$backendPath`""
Write-Host "Backend launcher opened. Use it to start Gateway and microservices." -ForegroundColor Green
Write-Host "Gateway URL: http://localhost:8070" -ForegroundColor White
Write-Host ""

Write-Host "Step 2: Opening smart-frontend dev server..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", "`"$frontendPath`""
Write-Host "Frontend launcher opened." -ForegroundColor Green
Write-Host "Frontend URL: http://localhost:5173" -ForegroundColor White
Write-Host ""

Write-Host "Access URLs:" -ForegroundColor Yellow
Write-Host "1. Frontend: http://localhost:5173" -ForegroundColor White
Write-Host "2. Gateway:  http://localhost:8070" -ForegroundColor White
Write-Host ""
Write-Host "Tip: close the service windows to stop services." -ForegroundColor White

Read-Host "Press Enter to close this window"
