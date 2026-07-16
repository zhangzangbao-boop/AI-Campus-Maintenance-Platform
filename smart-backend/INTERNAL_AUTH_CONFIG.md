# 内部服务认证配置说明

## 环境变量

### INTERNAL_SERVICE_SECRET（必填）

用于服务间内部调用的认证密钥，必须配置。

**配置方式：**

```bash
# Linux/macOS
export INTERNAL_SERVICE_SECRET="your-secure-random-secret-here"

# Windows PowerShell
$env:INTERNAL_SERVICE_SECRET="your-secure-random-secret-here"

# 或在启动命令中
java -DINTERNAL_SERVICE_SECRET="your-secure-random-secret-here" -jar app.jar
```

**安全要求：**
- 最小长度：32 字符
- 推荐使用随机生成：`openssl rand -base64 32`
- 生产环境必须配置（未配置时推送接口返回 500）
- 开发测试环境可使用测试密钥

**影响范围：**
- `qiyun-ops-service`：内部推送接口需要此密钥认证
- `qiyun-repair-service`：调用推送接口时携带此密钥

## 本地开发配置

### 方式一：环境变量（推荐）

```bash
# 创建本地环境变量文件
echo 'INTERNAL_SERVICE_SECRET=dev-secret-for-local-testing-only' > .env.local
```

### 方式二：IDE 配置

在 IntelliJ IDEA 中：
1. Run → Edit Configurations
2. Environment variables: `INTERNAL_SERVICE_SECRET=dev-secret-for-local-testing-only`

### 方式三：启动参数

```bash
# qiyun-ops-service
java -DINTERNAL_SERVICE_SECRET="dev-secret" -jar qiyun-ops-service.jar

# qiyun-repair-service
java -DINTERNAL_SERVICE_SECRET="dev-secret" -jar qiyun-repair-service.jar
```

## 生产环境配置

**禁止事项：**
- 禁止硬编码密钥到代码中
- 禁止提交真实密钥到 Git 仓库
- 禁止使用默认值（代码中已移除默认值）

**推荐方式：**
- Kubernetes：使用 Secrets
- Docker Compose：使用 `.env` 文件（不提交到仓库）
- 传统部署：使用环境变量或配置管理系统

## 故障排查

### 推送失败：服务配置错误

**日志：** `INTERNAL_SERVICE_SECRET 环境变量未配置，拒绝内部推送请求`

**解决：** 在 `qiyun-ops-service` 所在环境配置 `INTERNAL_SERVICE_SECRET` 环境变量

### 推送失败：认证失败

**日志：** `内部推送接口认证失败`

**解决：** 确保 `qiyun-repair-service` 和 `qiyun-ops-service` 配置了相同的密钥值

### 推送跳过

**日志：** `INTERNAL_SERVICE_SECRET 未配置，跳过实时推送`

**解决：** 在 `qiyun-repair-service` 所在环境配置 `INTERNAL_SERVICE_SECRET` 环境变量