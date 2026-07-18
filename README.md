# 启云校园智慧维修平台

## 项目简介

启云校园智慧维修平台是一个基于 **Spring Cloud 微服务架构** 的校园设施报修管理系统，支持学生报修、维修工处理、管理员运营和 AI 智能分析等完整业务流程。

**当前完成状态**：核心业务流程已完整实现，包括用户认证、工单管理、任务分配、数据统计、AI 智能分析和系统运维等功能模块。

## 技术栈

### 后端
- **Spring Boot** 3.3.4
- **Spring Cloud** 2023.0.3
- **Spring Cloud Alibaba (Nacos)** 2023.0.1.2
- **Spring Security** + JWT
- **Spring Data JPA** / Hibernate
- **Spring Cloud Gateway**
- **OpenFeign**
- **MySQL** 8.0+
- **Java** 17

### 前端
- **React** 19
- **Vite**
- **Ant Design** 5
- **React Router**
- **Ant Design Charts**

## 微服务架构

### 服务清单

| 服务 | 端口 | Nacos 服务名 | 职责 |
|------|------|--------------|------|
| qiyun-gateway | 8070 | qiyun-gateway | API 网关、统一路由、CORS |
| qiyun-user-service | 9003 | qiyun-user-service | 用户认证、用户管理 |
| qiyun-repair-service | 9004 | qiyun-repair-service | 报修工单、任务管理、分类 |
| qiyun-ops-service | 9005 | qiyun-ops-service | 统计分析、通知、知识库、备份 |
| qiyun-biz-service | 9001 | qiyun-biz-service | 旧业务兼容与维修工业务接口 |
| qiyun-ai-service | 9002 | qiyun-ai-service | AI 智能分析 |

### 项目结构

```
smart-backend/
├── qiyun-common/           # 公共工具类
├── qiyun-feign-api/        # Feign 客户端接口
├── qiyun-gateway/          # API 网关
├── qiyun-user-service/     # 用户服务
├── qiyun-repair-service/   # 报修服务
├── qiyun-ops-service/      # 运维服务
├── qiyun-biz-service/      # 业务兼容服务
└── qiyun-ai-service/       # AI 服务
```

### Gateway 路由规则

| 路径模式 | 目标服务 | 说明 |
|----------|----------|------|
| `/api/ai/**` | qiyun-ai-service | AI 智能分析 |
| `/api/auth/**` | qiyun-user-service | 认证登录 |
| `/api/users/**` | qiyun-user-service | 用户接口 |
| `/api/admin/users/**` | qiyun-user-service | 管理员用户管理 |
| `/api/repair-orders/**` | qiyun-repair-service | 报修工单 |
| `/api/tasks/**` | qiyun-repair-service | 维修任务 |
| `/api/categories/**` | qiyun-repair-service | 分类管理 |
| `/api/upload/**` | qiyun-repair-service | 文件上传 |
| `/api/notifications/**` | qiyun-ops-service | 站内通知 |
| `/api/knowledge-base/**` | qiyun-ops-service | 知识库 |
| `/api/admin/stats/**` | qiyun-ops-service | 统计分析 |
| `/api/admin/backup/**` | qiyun-ops-service | 系统备份 |
| `/api/admin/audit-logs/**` | qiyun-ops-service | 审计日志 |
| `/api/admin/knowledge-base/**` | qiyun-ops-service | 知识库管理 |
| `/api/admin/feedbacks/**` | qiyun-ops-service | 反馈管理 |

### 服务调用关系

```
┌─────────────┐
│   前端      │
│ (React)     │
└──────┬──────┘
       │ HTTP :8070
       ▼
┌─────────────────────────────────────────────┐
│           qiyun-gateway (8070)              │
│  - JWT 验证                                 │
│  - 路由转发                                 │
│  - CORS 处理                                │
└──────┬──────────┬──────────┬──────────┬─────┘
       │          │          │          │
       ▼          ▼          ▼          ▼
   ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
   │ user   │ │ repair │ │  ops   │ │   ai   │
   │ :9003  │ │ :9004  │ │ :9005  │ │ :9002  │
   └────────┘ └────────┘ └────────┘ └────────┘
       │          │          │          │
       └──────────┴──────────┴──────────┘
                      │
              ┌───────┴───────┐
              │    MySQL      │
              │   repairdb    │
              └───────────────┘
```

**OpenFeign 调用**：
- `qiyun-ai-service` → 被其他服务调用进行 AI 分析
- `qiyun-ops-service` → 通过 Feign 调用 `qiyun-repair-service` 内部接口获取统计数据

## 功能模块

### 学生端
- ✅ 用户注册与登录
- ✅ 提交报修申请（支持图片上传）
- ✅ AI 智能填写报修表单
- ✅ 查看我的报修单列表与详情
- ✅ 查看报修进度与时间线
- ✅ 评价维修服务（速度、质量、态度、是否解决、匿名）
- ✅ 个人信息管理与密码修改

### 维修工端
- ✅ 查看分配的任务列表
- ✅ 任务状态管理（接单、到场确认、完成）
- ✅ 添加维修过程记录
- ✅ 申请转派任务
- ✅ 个人工作台统计
- ✅ 个人信息管理

### 管理员端
- ✅ 用户管理（学生、维修工增删改查）
- ✅ 工单管理（分配、驳回、状态跟踪）
- ✅ 智能派单推荐
- ✅ 数据统计分析（分类、地点、月度、评分等）
- ✅ 系统备份与恢复
- ✅ 反馈管理
- ✅ 密码重置
- ✅ 维修知识库管理
- ✅ 审计日志查询
- ✅ 系统配置管理
- ✅ 转派申请审批

### AI 智能体
- ✅ 智能分析报修单（分类、优先级、建议）
- ✅ 相似工单推荐
- ✅ 维修报告生成
- ✅ 工单摘要生成
- ✅ 知识库草稿生成
- ✅ 规则引擎分析（支持离线演示）

## 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `NACOS_SERVER_ADDR` | 127.0.0.1:8848 | Nacos 服务地址 |
| `DB_URL` | jdbc:mysql://127.0.0.1:3306/repairdb | 数据库连接 |
| `DB_USERNAME` | root | 数据库用户名 |
| `DB_PASSWORD` | - | 数据库密码 |
| `JWT_SECRET` | - | JWT 密钥（必须配置） |
| `JWT_EXPIRATION_MS` | 604800000 | JWT 有效期（7天） |
| `UPLOAD_DIRECTORY` | ./uploads | 上传文件目录 |
| `BACKUP_DIRECTORY` | ./backups | 备份目录 |
| `BACKUP_AUTO_ENABLED` | false | 自动备份开关 |
| `INTERNAL_SERVICE_SECRET` | - | 内部服务调用密钥（必须配置） |
| `CHROMA_URL` | http://localhost:8000 | Chroma 向量数据库地址 |
| `CHROMA_COLLECTION` | campus_maintenance_kb | Chroma 集合名称 |
| `EMBEDDING_MODEL_PATH` | - | ONNX Embedding 模型路径（RAG 功能必需） |
| `DEEPSEEK_API_KEY` | - | DeepSeek API 密钥（AI 生成回答） |

## 快速启动

> 当前项目已统一为 `smart-backend` 微服务后端 + `smart-frontend` 前端。根目录旧单体 `backend` 已移除，不要再使用 `start-all.bat` / `start-all.ps1`。本地一键启动入口为根目录 `start-project.bat`。

### 前置条件

- **Java** 17+
- **Maven** 3.6+
- **MySQL** 8.0+
- **Node.js** 18+
- **Nacos** 2.x

### 1. 拉取代码并进入项目

```bash
git clone <repository-url>
cd AI-Campus-Maintenance-Platform
```

### 2. 启动外部依赖

本地启动前请先确认：

- MySQL 已启动，端口为 `3306`
- Nacos 已启动，端口为 `8848`
- Nacos 控制台可访问：`http://localhost:8848/nacos/`

Nacos standalone 模式示例：

```bash
sh startup.sh -m standalone
```

Windows 环境可使用 Nacos 自带的 `startup.cmd -m standalone`。

### 3. 初始化数据库

```sql
-- 创建数据库
CREATE DATABASE repairdb DEFAULT CHARACTER SET utf8mb4;

-- 导入初始数据
SOURCE smart-backend/qiyun-repair-service/src/main/resources/full_init_test_data.sql;
```

### 4. 配置本地 `.env`

在项目根目录创建 `.env` 文件。不要提交 `.env`，只提交 `.env.example` 这类占位模板。

```env
NACOS_SERVER_ADDR=127.0.0.1:8848
DB_URL=jdbc:mysql://127.0.0.1:3306/repairdb?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=root
DB_PASSWORD=your-db-password
JWT_SECRET=your-at-least-32-characters-jwt-secret-key
INTERNAL_SERVICE_SECRET=your-internal-service-secret
JWT_EXPIRATION_MS=604800000
```

如需启用 RAG / AI 扩展功能，再按实际环境补充 `CHROMA_URL`、`EMBEDDING_MODEL_PATH`、`DEEPSEEK_API_KEY` 等变量。

### 5. 启动项目（推荐）

Windows 本地开发直接双击根目录：

```text
start-project.bat
```

该脚本会自动：

- 读取根目录 `.env`
- 检查 MySQL `3306` 和 Nacos `8848`
- 使用项目内 `local-cache/maven` 构建并启动 `smart-backend` 各微服务
- 启动 `smart-frontend`
- 打开前端页面 `http://localhost:5173/`

启动后保持弹出的后端和前端窗口不要关闭。

### 6. 访问地址

| 地址 | 说明 |
|------|------|
| `http://localhost:5173/` | 前端页面 |
| `http://localhost:8070/` | Gateway 端口，根路径返回 404 属于正常现象 |
| `http://localhost:8848/nacos/` | Nacos 控制台 |

### 7. 手动启动后端（备用）

如一键脚本不可用，可手动启动：

```bash
cd smart-backend
mvn -Dmaven.repo.local=../local-cache/maven -DskipTests install

cd qiyun-user-service
mvn -Dmaven.repo.local=../../local-cache/maven spring-boot:run

cd ../qiyun-repair-service
mvn -Dmaven.repo.local=../../local-cache/maven spring-boot:run

cd ../qiyun-ops-service
mvn -Dmaven.repo.local=../../local-cache/maven spring-boot:run

cd ../qiyun-ai-service
mvn -Dmaven.repo.local=../../local-cache/maven spring-boot:run

cd ../qiyun-biz-service
mvn -Dmaven.repo.local=../../local-cache/maven spring-boot:run

cd ../qiyun-gateway
mvn -Dmaven.repo.local=../../local-cache/maven spring-boot:run
```

### 8. 手动启动前端（备用）

```bash
cd smart-frontend
npm install
npm run dev
```

访问 `http://localhost:5173/`。

### 9. 启动 Chroma 向量数据库（RAG 功能需要）

```bash
# 使用 Docker 启动 Chroma
docker run -d -p 8000:8000 chromadb/chroma:0.5.20

# 或使用 pip 安装并启动
pip install chromadb==0.5.20
chroma run --host 0.0.0.0 --port 8000
```

### 10. 下载 Embedding 模型（RAG 功能需要）

下载 `paraphrase-multilingual-MiniLM-L12-v2` ONNX 模型并配置路径：

```bash
# 模型下载地址（需自行转换或下载预转换版本）
# 设置环境变量指向模型文件
export EMBEDDING_MODEL_PATH="/path/to/model.onnx"
```

**注意**：如不配置 Embedding 模型，RAG 问答功能将不可用，系统会返回明确的降级提示。

## Postman 集合

项目提供完整的 Postman API 集合，位于 `postman/` 目录：

| 文件 | 说明 |
|------|------|
| `Campus_Maintenance_API.postman_collection.json` | 完整 API 集合（78 个接口） |
| `Campus_Maintenance_Local.postman_environment.json` | 本地环境变量 |
| `README.md` | 使用说明 |

### 导入步骤

1. 打开 Postman → Import → 选择 `Campus_Maintenance_API.postman_collection.json`
2. 导入环境：Manage Environments → Import → 选择 `Campus_Maintenance_Local.postman_environment.json`
3. 激活环境：右上角下拉框选择"校园维修平台-本地环境"

### 测试账号

测试账号密码详见 `smart-backend/qiyun-repair-service/src/main/resources/full_init_test_data.sql`

登录页提供管理员、维修工、学生三个快捷填充按钮，方便演示时快速切换角色。

## API 文档

### 认证接口
```
POST /api/auth/login          - 用户登录
POST /api/auth/register       - 用户注册（学生）
```

### 用户接口
```
GET  /api/users/me            - 获取当前用户信息
PUT  /api/users/me            - 更新用户信息
PUT  /api/users/me/password   - 修改密码
```

### 报修工单
```
POST   /api/repair-orders              - 创建报修工单
GET    /api/repair-orders/my           - 获取我的工单列表
GET    /api/repair-orders/{id}         - 获取工单详情
DELETE /api/repair-orders/{id}         - 删除工单
POST   /api/repair-orders/{id}/evaluate - 评价工单
```

### 维修任务
```
GET  /api/tasks/my                     - 获取我的任务列表
GET  /api/staff/dashboard              - 维修工工作台统计
POST /api/tasks/{id}/accept            - 接单
PUT  /api/tasks/{id}/arrive            - 到场确认
POST /api/tasks/{id}/process-records   - 添加维修记录
PUT  /api/tasks/{id}/resolve           - 完成工单
POST /api/tasks/{id}/transfer-request  - 申请转派
```

### 管理员接口
```
GET  /api/admin/users                  - 用户列表
POST /api/admin/users                  - 创建用户
PUT  /api/admin/users/{id}             - 更新用户
POST /api/admin/users/{id}/reset-password - 重置密码
DELETE /api/admin/users/{id}           - 禁用用户

GET  /api/admin/repair-orders          - 工单列表
PUT  /api/admin/repair-orders/{id}/assign - 分配工单
GET  /api/admin/repair-orders/{id}/recommend-staff - 推荐维修工

GET  /api/admin/stats/*                - 各类统计接口
GET  /api/admin/feedbacks              - 反馈列表
GET  /api/admin/backup/*               - 备份管理
```

### AI 接口
```
POST /api/ai/analyze-ticket            - 智能分析报修单
GET  /api/ai/health                    - AI 服务健康检查
```

## 已完成内容

### 后端
- ✅ Spring Cloud 微服务架构
- ✅ Nacos 服务注册与发现
- ✅ Gateway 统一网关与路由
- ✅ JWT 认证与 RBAC 权限控制
- ✅ OpenFeign 服务间调用
- ✅ 完整的用户、工单、任务、统计 API
- ✅ AI 智能分析集成
- ✅ 文件上传与备份管理

### 前端
- ✅ 学生端：报修、查询、评价、AI 智能填写
- ✅ 维修工端：任务管理、过程记录、转派申请
- ✅ 管理员端：用户管理、工单分配、统计分析、运维中心
- ✅ 统一 UI 框架与导航
- ✅ 实时通知与消息中心

## 暂未完善

- ⏳ WebSocket 实时推送通知
- ⏳ 移动端适配
- ⏳ 真实 AI 模型集成（当前为规则引擎）
- ⏳ SLA 自动升级告警
- ⏳ 工单相似度深度匹配

## 后续计划

1. **P0**：完善前端与后端 API 对齐，确保所有接口正常调用
2. **P1**：集成真实 AI 模型（DeepSeek/Claude）
3. **P1**：实现 WebSocket 实时通知
4. **P2**：移动端 H5 适配
5. **P2**：SLA 监控与自动升级

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

### 代码结构约定
- 后端包名：`com.qiyun.{service}` 或 `com.ligong.reportingcenter`
- 数据库列名：`snake_case`，Java 字段：`camelCase`
- 统一使用 DTO 进行 API 响应
- 异常处理使用 `BusinessException`

## 许可证

本项目仅供学习使用。
