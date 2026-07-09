# 校园报修管理系统 - 前端启动脚本
# 启动前端开发服务器

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "校园报修管理系统 - 前端服务启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "正在启动前端开发服务器..." -ForegroundColor Yellow
Write-Host "前端地址: http://localhost:3000" -ForegroundColor White
Write-Host "API 代理: http://localhost:8080/api" -ForegroundColor White
Write-Host ""

# 启动 Vite 开发服务器
try {
    npm run dev
} catch {
    Write-Host "启动失败！请检查错误信息。" -ForegroundColor Red
    Write-Host "可能的原因：" -ForegroundColor Yellow
    Write-Host "1. npm 依赖未安装（请先运行 npm install）" -ForegroundColor White
    Write-Host "2. 端口 3000 已被占用" -ForegroundColor White
    Read-Host "按任意键退出..."
}