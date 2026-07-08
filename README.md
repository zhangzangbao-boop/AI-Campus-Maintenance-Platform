# 校园报修管理系统

一个基于 Spring Boot + React 的校园报修管理系统，支持学生报修、维修工处理、管理员管理的完整业务流程。

## 项目结构

```
-reportingcenter/
├── backend/              # 后端项目 (Spring Boot)
│   ├── src/             # 源代码
│   │   ├── main/
│   │   │   ├── java/    # Java 代码
│   │   │   └── resources/ # 资源文件
│   │   └── test/        # 测试代码
│   ├── pom.xml          # Maven 配置
│   └── README.md        # 后端说明文档
├── frontend/             # 前端项目 (React + Vite)
│   ├── src/             # 源代码
│   │   ├── Admin/      # 管理员模块
│   │   ├── Student/    # 学生模块
│   │   ├── Worker/     # 维修工模块
│   │   └── services/   # API 服务
│   ├── package.json    # 依赖配置
│   └── vite.config.js  # Vite 配置
│   └── README.md        # 前端说明文档
└── README.md             # 本文件
```

## 技术栈

### 后端
- Spring Boot 3.x
- Spring Security + JWT
- JPA / Hibernate
- MySQL
- Maven

### 前端
- React 19
- Vite（构建工具）
- Ant Design 5
- React Router
- Ant Design Charts（数据可视化）

## 快速开始

### 🚀 一键启动（推荐）

**最简单的方式**：使用项目提供的启动脚本

#### Windows 批处理脚本（推荐）

**双击运行**或在命令行运行：

```bash
# 一键启动前后端
start-all.bat
```

或在项目根目录双击 `start-all.bat` 文件。

这将自动：
1. 启动后端服务（http://localhost:8080）
2. 启动前端开发服务器（http://localhost:3000）
3. 在两个独立的命令行窗口中运行

#### 单独启动后端

```bash
cd backend
start-backend.bat
```

或双击 `backend/start-backend.bat` 文件。

#### 单独启动前端

```bash
cd frontend
start-frontend.bat
```

或双击 `frontend/start-frontend.bat` 文件。

#### PowerShell 脚本（需设置执行策略）

如果已设置 PowerShell 执行策略，可使用：

```powershell
.\start-all.ps1
```

---

### 📋 手动启动

#### 后端启动

```bash
cd backend
mvn spring-boot:run
```

访问：http://localhost:8080

#### 前端开发

```bash
cd frontend
npm install  # 首次运行需要安装依赖
npm run dev
```

开发服务器运行在 http://localhost:3000，API 请求自动代理到后端。

---

### 🔨 前端构建与集成

```bash
cd frontend
npm run build
```

构建产物自动输出到 `backend/src/main/resources/static/`，与后端集成。

启动后端后访问 http://localhost:8080 即可使用完整应用。

---

### ⚙️ 环境要求

- **Java**: JDK 21+ (启动脚本自动配置)
- **Maven**: 3.6+
- **Node.js**: 18+
- **MySQL**: 8.0+ (需提前启动服务)

---

### 📝 首次运行注意事项

1. **数据库配置**：
   - 确保 MySQL 服务已启动
   - 推荐用 `backend/src/main/resources/full_init_test_data.sql` 一键初始化 `repairdb`
   - 该脚本会重建项目表并插入完整测试数据，重复运行会清空已有项目数据

2. **前端依赖**：
   - 首次运行前端需要先执行 `npm install`

3. **启动顺序**：
   - 建议先启动后端，等待数据库初始化完成
   - 再启动前端开发服务器

4. **PowerShell 执行策略**（仅对 .ps1 脚本）：
   - 如果遇到"禁止运行脚本"错误，使用 .bat 脚本
   - 或设置执行策略：`Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

## 功能特性

### 学生端
- 提交报修申请（支持图片上传）
- AI 智能填写报修表单，并提示相似历史工单和安全注意事项
- 查看我的报修单列表
- 查看报修单详情和进度
- 评价维修服务
- 个人信息管理

### 维修工端
- 查看分配的任务列表
- 开始/完成维修任务
- 添加维修记录和说明
- 查看任务详情
- 个人信息管理

### 管理员端
- 用户管理（学生、维修工的增删改查）
- 工单管理（分配、驳回、状态跟踪）
- 数据统计分析（分类统计、地点统计、评分统计）
- 校园设施健康指数、热点问题分析和智能运维中心
- 维修知识库、系统配置和审计日志管理
- 系统备份与恢复
- 反馈管理
- 密码重置

## 数据库配置

在 `backend/src/main/resources/application.yml` 中配置数据库连接：

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/repairdb}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:1234}
```

本地演示推荐在 MySQL 命令行中执行：

```sql
SOURCE E:/Software_System_Design_and_Development_Practice/Campus-Maintenance-System/backend/src/main/resources/full_init_test_data.sql;
```

预置测试账号密码统一为 `123456`：

- 管理员：`admin`、`admin02`
- 维修员：`worker001`、`worker002`、`worker003`、`worker004`
- 学生：`20260001`、`20260002`、`20260003`、`20260004`、`20260005`、`20260006`、`20260007`、`20260008`

登录页提供管理员、维修员、学生三个快捷填充按钮，适合答辩演示时快速切换角色。

## 智能特色配置

系统保留 DeepSeek 大模型接入，未配置外部服务时会自动使用本地规则引擎和关键词相似度，方便课程离线演示。

```yaml
ai:
  enabled: false
  provider: deepseek
  base-url: https://api.deepseek.com
  api-key: ${DEEPSEEK_API_KEY:}
  model: ${DEEPSEEK_MODEL:deepseek-v4-flash}
```

启用 DeepSeek 时：

```powershell
$env:DEEPSEEK_API_KEY="你的 DeepSeek API Key"
$env:DEEPSEEK_MODEL="deepseek-v4-flash"
$env:JWT_SECRET="至少32位的生产JWT密钥"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="1234"
$env:VITE_API_BASE_URL="http://localhost:8080/api"
```

然后把 `application.yml` 中 `ai.enabled` 改为 `true`，也可以在管理员端 `智能运维中心 -> 系统配置` 维护 `ai.enabled`、`ai.api-key`、`ai.model`、SLA 时限和图片上传限制。启动后进入 `智能运维中心 -> DeepSeek AI 服务` 查看状态。

后续完整收口已补齐：

- 登录页测试账号一键填充。
- 个人中心支持当前用户修改密码。
- 学生评价支持速度、质量、态度、是否解决、匿名展示。
- 维修人员转派申请可在管理员 `智能运维中心 -> 转派审核` 审批。
- 图片上传数量和单张大小读取系统配置，未配置时默认 5 张、5MB。
- 管理员可在维修知识库中使用 AI 生成知识条目草稿。
- 未登录和无权限访问统一返回中文 JSON 错误。
- JWT 密钥和有效期支持环境变量配置。
- 备份恢复前会自动创建保护备份，定时备份可通过 `backup.auto-enabled` 和 `backup.cron` 开启。
- 维修人员支持到场确认、维修过程记录和转派申请的专用接口。

## API 文档

主要 API 端点：

- `/api/auth/**` - 认证相关
- `/api/tickets/**` - 报修单管理
- `/api/users/**` - 用户管理
- `/api/categories/**` - 分类管理
- `/api/admin/backup/**` - 备份管理（需管理员权限）
- `/api/ai/**` - AI 智能报修、工单摘要、相似工单、维修报告和知识库草稿
- `/api/knowledge-base/**` - 维修知识库查询
- `/api/notifications/**` - 站内通知

详细 API 文档请参考后端 README.md。

## 开发指南

### 分支管理
- `main` - 生产分支
- `develop` - 开发分支
- `feature/*` - 功能分支

### 提交规范
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `refactor`: 代码重构
- `test`: 测试相关

## 部署说明

### 后端部署

```bash
cd backend
mvn clean package
java -jar target/repairing-center-0.0.1-SNAPSHOT.jar
```

### 前端部署

前端已配置自动集成，构建产物直接输出到后端静态资源目录：

```bash
cd frontend
npm run build  # 自动输出到 backend/src/main/resources/static/
```

或使用一体化构建：

```bash
# 在项目根目录
cd frontend && npm run build && cd ../backend && mvn clean package
```

## 许可证

本项目仅供学习使用。
