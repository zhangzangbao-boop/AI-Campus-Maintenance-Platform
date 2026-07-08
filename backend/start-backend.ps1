# 校园报修管理系统 - 后端启动脚本
# 自动设置 Java 21 环境并启动后端服务

# 设置 Java 21 环境
$env:JAVA_HOME = "D:\jdk21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 显示当前 Java 版本
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "校园报修管理系统 - 后端服务启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "使用 Java 版本:" -ForegroundColor Yellow
$javaVersion = & java -version 2>&1 | Select-Object -First 1
Write-Host $javaVersion -ForegroundColor Green
Write-Host "JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Green
Write-Host ""
Write-Host "正在启动后端服务..." -ForegroundColor Yellow
Write-Host "后端地址: http://localhost:8080" -ForegroundColor White
Write-Host ""

# 启动 Spring Boot 应用
try {
    mvn spring-boot:run
} catch {
    Write-Host "启动失败！请检查错误信息。" -ForegroundColor Red
    Write-Host "可能的原因：" -ForegroundColor Yellow
    Write-Host "1. 数据库未启动或连接配置错误" -ForegroundColor White
    Write-Host "2. 端口 8080 已被占用" -ForegroundColor White
    Write-Host "3. Maven 依赖未安装" -ForegroundColor White
    Read-Host "按任意键退出..."
}