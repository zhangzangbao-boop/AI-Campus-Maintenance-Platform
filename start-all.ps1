# 校园报修管理系统 - 一键启动脚本
# 同时启动前后端服务（需要在两个终端窗口中运行）

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "校园报修管理系统 - 一键启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否在项目根目录
$backendPath = Join-Path $PSScriptRoot "backend\start-backend.ps1"
$frontendPath = Join-Path $PSScriptRoot "frontend\start-frontend.ps1"

if (-not (Test-Path $backendPath)) {
    Write-Host "错误：找不到后端启动脚本" -ForegroundColor Red
    Write-Host "请确保在项目根目录运行此脚本" -ForegroundColor Yellow
    Read-Host "按任意键退出..."
    exit 1
}

if (-not (Test-Path $frontendPath)) {
    Write-Host "错误：找不到前端启动脚本" -ForegroundColor Red
    Write-Host "请确保在项目根目录运行此脚本" -ForegroundColor Yellow
    Read-Host "按任意键退出..."
    exit 1
}

Write-Host "项目结构检查完成" -ForegroundColor Green
Write-Host ""

# 启动后端（新窗口）
Write-Host "步骤1: 启动后端服务..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy Bypass", "-File `"$backendPath`""
Write-Host "后端服务已在新窗口启动" -ForegroundColor Green
Write-Host "后端地址: http://localhost:8080" -ForegroundColor White
Write-Host ""

# 等待3秒让后端先启动
Write-Host "等待后端初始化..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

# 启动前端（新窗口）
Write-Host "步骤2: 启动前端服务..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy Bypass", "-File `"$frontendPath`""
Write-Host "前端服务已在新窗口启动" -ForegroundColor Green
Write-Host "前端地址: http://localhost:3000" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "✅ 启动完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "访问方式：" -ForegroundColor Yellow
Write-Host "1. 前端开发地址: http://localhost:3000" -ForegroundColor White
Write-Host "2. 后端直接访问: http://localhost:8080" -ForegroundColor White
Write-Host ""
Write-Host "提示：" -ForegroundColor Yellow
Write-Host "- 前后端服务已在独立窗口运行" -ForegroundColor White
Write-Host "- 关闭窗口即可停止服务" -ForegroundColor White
Write-Host "- 首次启动请等待数据库初始化完成" -ForegroundColor White
Write-Host ""

Read-Host "按任意键关闭此窗口（前后端将继续运行）..."