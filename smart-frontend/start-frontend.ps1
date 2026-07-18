# AI Campus Maintenance Platform - smart-frontend launcher

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AI Campus Maintenance Platform Frontend" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting Vite development server..." -ForegroundColor Yellow
Write-Host "Frontend URL: http://localhost:5173" -ForegroundColor White
Write-Host "API proxy: http://localhost:8070/api" -ForegroundColor White
Write-Host ""

try {
    Set-Location -LiteralPath $PSScriptRoot
    npm run dev
} catch {
    Write-Host "Startup failed. Please check the error output above." -ForegroundColor Red
    Write-Host "Possible causes:" -ForegroundColor Yellow
    Write-Host "1. npm dependencies are not installed." -ForegroundColor White
    Write-Host "2. Port 5173 is already in use." -ForegroundColor White
    Read-Host "Press Enter to exit"
}
