# 启云校园智慧维修平台

> AI Campus Maintenance Platform。当前 README 基于 2026-07-24 项目最终版代码整理。

## 项目简介

启云校园智慧维修平台是一套面向校园后勤维修场景的综合管理系统。平台覆盖学生报修、管理员派单、维修人员处理、学生确认评价、运营统计、通知公告、知识库和 AI 辅助分析等完整闭环。

系统采用前后端分离架构：

- 前端：React + Vite + Ant Design，提供学生端、维修工端、管理员端三个角色工作台。
- 后端：Spring Boot + Spring Cloud 微服务架构，通过 Nacos 做服务发现，通过 Spring Cloud Gateway 暴露统一 API 入口。
- AI：支持 DeepSeek/OpenAI-Compatible 大模型配置，同时内置规则引擎降级能力，保证无外部 API Key 时仍可完成智能识别演示。

## 当前最终版能力

### 学生端

- 账号注册、登录、个人资料和头像修改、密码修改
- 提交报修申请，支持文字、位置、分类、紧急程度和图片上传
- AI 智能识别报修描述，自动生成标题、位置、分类、紧急程度和维修建议
- 查看我的报修、进度时间线、处理记录、留言与催办
- 对已完成工单进行确认、退回处理和评价反馈
- 查看校园公告、置顶公告和维修知识问答

### 维修工端

- 查看今日任务、处理中任务、高优先级任务、超时预警和历史记录
- 接收管理员派单，更新任务状态，到场确认，提交完成处理
- 添加维修过程记录，支持多图过程记录
- 发起转派申请
- 查看个人工作台统计、评分和重点关注任务
- 查看公告、知识问答，维护个人资料与头像

### 管理员端

- 工单池管理：搜索、分配、驳回、状态更新、处理备注、预计完成时间
- 智能派单推荐：结合维修人员任务量、经验、评分等指标辅助分配
- 用户管理：学生、维修工、管理员的创建、编辑、禁用和密码重置
- 数据分析：状态、分类、地点、月度趋势、维修工评分、处理时长、SLA、热点、设施健康、故障趋势
- 反馈管理：评价列表、回访/跟进状态维护
- 公告管理：创建、编辑、发布、撤回、删除公告
- 运维中心：系统配置、知识库、审计日志、数据库备份与恢复
- AI 分析修正、历史案例索引重建、知识库索引重建

## 技术栈

### 后端

- Java 17
- Spring Boot 3.3.4
- Spring Cloud 2023.0.3
- Spring Cloud Alibaba Nacos 2023.0.1.2
- Spring Cloud Gateway
- Spring Security + JWT
- Spring Data JPA / Hibernate
- OpenFeign
- MySQL 8.0+
- Maven

### 前端

- React 19
- Vite / rolldown-vite
- Ant Design 5
- Ant Design Charts
- React Router
- STOMP / SockJS WebSocket 客户端

### AI 与知识库

- DeepSeek/OpenAI-Compatible Chat API，可选
- 本地规则引擎，可离线降级
- Chroma 向量库，可选
- ONNX Embedding 模型，默认路径为 `local-models/paraphrase-multilingual-MiniLM-L12-v2/`

## 项目结构

```text
AI-Campus-Maintenance-Platform/
├── smart-backend/                         # Spring Cloud 微服务后端
│   ├── qiyun-common/                      # 公共异常、响应、工具等
│   ├── qiyun-feign-api/                   # 服务间 Feign 接口
│   ├── qiyun-gateway/                     # 统一网关，端口 8070
│   ├── qiyun-user-service/                # 用户认证、个人信息、用户管理
│   ├── qiyun-repair-service/              # 报修工单、维修任务、图片上传
│   ├── qiyun-ops-service/                 # 通知、公告、统计、知识库、备份、审计
│   ├── qiyun-ai-service/                  # AI 智能分析、RAG 问答、报告生成
│   └── qiyun-biz-service/                 # 兼容服务与数据库初始化 SQL 资源
├── smart-frontend/                        # React 前端
│   ├── src/Student/                       # 学生端页面
│   ├── src/Worker/                        # 维修工端页面
│   ├── src/Admin/                         # 管理员端页面
│   ├── src/components/                    # 通用业务组件
│   └── src/services/                      # API、WebSocket、个人资料等服务封装
├── postman/                               # Postman 集合与本地环境
├── docs/                                  # 测试说明与补充文档
├── local-models/                          # 本地 Embedding 模型目录
├── start-project.ps1                      # Windows 一键启动脚本
└── start-project.bat                      # Windows 一键启动入口
```

## 微服务与端口

| 服务 | 默认端口 | Nacos 服务名 | 主要职责 |
| --- | ---: | --- | --- |
| qiyun-gateway | 8070 | qiyun-gateway | API 统一入口、路由、CORS、WebSocket 转发 |
| qiyun-user-service | 9003 | qiyun-user-service | 登录注册、当前用户、管理员用户管理、内部用户查询 |
| qiyun-repair-service | 9004 | qiyun-repair-service | 报修工单、维修任务、过程记录、评论、图片上传、派单 |
| qiyun-ops-service | 9005 | qiyun-ops-service | 通知、公告、统计分析、反馈管理、知识库、备份、审计、系统配置 |
| qiyun-ai-service | 9002 | qiyun-ai-service | 工单分析、反馈情感分析、维修报告、RAG 问答 |
| qiyun-biz-service | 9001 | qiyun-biz-service | 兼容兜底服务、历史业务兼容、数据库 SQL 资源 |

## Gateway 路由

| 路径 | 目标服务 |
| --- | --- |
| `/ws/**` | qiyun-ops-service |
| `/api/ai/**` | qiyun-ai-service |
| `/api/auth/**` | qiyun-user-service |
| `/api/admin/users/**` | qiyun-user-service |
| `/api/users/**` | qiyun-user-service |
| `/uploads/**` | qiyun-repair-service |
| `/api/repair-orders/**` | qiyun-repair-service |
| `/api/tasks/**` | qiyun-repair-service |
| `/api/staff/**` | qiyun-repair-service |
| `/api/categories/**` | qiyun-repair-service |
| `/api/upload/**` | qiyun-repair-service |
| `/api/admin/repair-orders/**` | qiyun-repair-service |
| `/api/admin/transfer-requests/**` | qiyun-repair-service |
| `/api/notifications/**` | qiyun-ops-service |
| `/api/announcements/**` | qiyun-ops-service |
| `/api/knowledge-base/**` | qiyun-ops-service |
| `/api/admin/audit-logs/**` | qiyun-ops-service |
| `/api/admin/system-config/**` | qiyun-ops-service |
| `/api/admin/backup/**` | qiyun-ops-service |
| `/api/admin/knowledge-base/**` | qiyun-ops-service |
| `/api/admin/stats/**` | qiyun-ops-service |
| `/api/admin/feedbacks/**` | qiyun-ops-service |
| `/api/admin/announcements/**` | qiyun-ops-service |
| `/api/admin/export/**` | qiyun-ops-service |
| 其他 `/api/**` | qiyun-biz-service |

## 核心业务流程

```text
学生提交报修
  -> AI 智能识别分类/紧急程度/建议
  -> 生成工单 WAITING_ACCEPT
  -> 管理员查看工单池并智能派单
  -> 维修工处理 IN_PROGRESS
  -> 维修工到场、记录过程、提交完成 RESOLVED
  -> 学生确认 WAITING_FEEDBACK
  -> 学生评价 FEEDBACKED
  -> 管理员归档 CLOSED
```

工单也支持从待受理或处理中状态驳回，支持学生退回维修处理，支持维修工申请转派。

## 主要 API

### 认证与用户

```text
POST  /api/auth/login
POST  /api/auth/register
GET   /api/users/me
PUT   /api/users/me
PUT   /api/users/me/password
GET   /api/users?role=STUDENT|STAFF|ADMIN

GET    /api/admin/users
POST   /api/admin/users
PUT    /api/admin/users/{userId}
DELETE /api/admin/users/{userId}
POST   /api/admin/users/{userId}/reset-password
```

### 报修与维修任务

```text
POST   /api/repair-orders
GET    /api/repair-orders/my
GET    /api/repair-orders/{id}
DELETE /api/repair-orders/{id}
PUT    /api/repair-orders/{id}/confirm-completion
PUT    /api/repair-orders/{id}/reject-completion
POST   /api/repair-orders/{id}/evaluate
GET    /api/repair-orders/{id}/comments
POST   /api/repair-orders/{id}/comments
POST   /api/repair-orders/{id}/comments/urge
GET    /api/repair-orders/{id}/process-records
POST   /api/repair-orders/{id}/process-records/with-images

GET  /api/tasks/my
GET  /api/tasks/{id}
PUT  /api/tasks/{id}/status
PUT  /api/tasks/{id}/arrive
PUT  /api/tasks/{id}/complete
POST /api/tasks/{id}/process-records
POST /api/tasks/{id}/process-records/with-images
POST /api/tasks/{id}/transfer-request
GET  /api/staff/dashboard
```

### 管理员业务

```text
GET  /api/admin/repair-orders
PUT  /api/admin/repair-orders/{id}/assign
GET  /api/admin/repair-orders/{id}/recommend-staff
PUT  /api/admin/repair-orders/{id}/status
PUT  /api/admin/repair-orders/{id}/repair-notes
PUT  /api/admin/repair-orders/{id}/process-notes
PUT  /api/admin/repair-orders/{id}/estimated-completion-time
GET  /api/admin/transfer-requests
PUT  /api/admin/transfer-requests/{recordId}/decision

GET  /api/admin/stats/status
GET  /api/admin/stats/category
GET  /api/admin/stats/location
GET  /api/admin/stats/monthly
GET  /api/admin/stats/repairman-rating
GET  /api/admin/stats/processing-time
GET  /api/admin/stats/sla
GET  /api/admin/stats/hotspot
GET  /api/admin/stats/facility-health
GET  /api/admin/stats/fault-trends
POST /api/admin/stats/fault-trends/refresh

GET  /api/admin/feedbacks
PUT  /api/admin/feedbacks/{ratingId}/follow-up
GET  /api/admin/export
```

### 通知、公告、知识库与运维

```text
GET  /api/notifications
GET  /api/notifications/unread-count
PUT  /api/notifications/{id}/read
PUT  /api/notifications/read-all

GET    /api/announcements
GET    /api/announcements/{id}
GET    /api/admin/announcements
POST   /api/admin/announcements
PUT    /api/admin/announcements/{id}
POST   /api/admin/announcements/{id}/publish
POST   /api/admin/announcements/{id}/withdraw
DELETE /api/admin/announcements/{id}

GET    /api/knowledge-base/search
GET    /api/knowledge-base/recommend
GET    /api/admin/knowledge-base
POST   /api/admin/knowledge-base
PUT    /api/admin/knowledge-base/{id}
DELETE /api/admin/knowledge-base/{id}
POST   /api/admin/knowledge-base/rebuild-index

GET  /api/admin/audit-logs
GET  /api/admin/system-config
PUT  /api/admin/system-config/{key}
POST /api/admin/backup/create
GET  /api/admin/backup/list
GET  /api/admin/backup/status
POST /api/admin/backup/restore
DELETE /api/admin/backup/{fileName}
```

### AI

```text
GET  /api/ai/health
GET  /api/ai/status
POST /api/ai/analyze-ticket
POST /api/ai/analyze-feedback-sentiment
POST /api/ai/repair-report/generate
GET  /api/ai/rag/ask
GET  /api/ai/rag/status
```

## 本地运行

### 环境要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Node.js 18+
- Nacos 2.x
- 可选：Docker 或本地 Chroma，用于 RAG 向量检索

### 1. 启动外部依赖

启动 MySQL，确认端口为 `3306`。启动 Nacos 单机模式，确认端口为 `8848`。

Windows Nacos 示例：

```cmd
startup.cmd -m standalone
```

Linux/macOS Nacos 示例：

```bash
sh startup.sh -m standalone
```

Nacos 控制台地址：

```text
http://localhost:8848/nacos/
```

### 2. 初始化数据库

创建数据库：

```sql
CREATE DATABASE repairdb DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

导入最终版演示数据：

```bash
mysql -u root -p repairdb < smart-backend/qiyun-biz-service/src/main/resources/full_init_test_data.sql
```

该 SQL 包含完整表结构、演示账号、工单、评价、通知、公告、知识库、统计配置和 AI 分析相关表。

### 3. 配置根目录 `.env`

在项目根目录创建 `.env` 文件。不要提交真实 `.env`。

```env
NACOS_SERVER_ADDR=127.0.0.1:8848
DB_URL=jdbc:mysql://127.0.0.1:3306/repairdb?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=root
DB_PASSWORD=your-db-password
JWT_SECRET=your-at-least-32-characters-jwt-secret
JWT_EXPIRATION_MS=604800000
INTERNAL_SERVICE_SECRET=qiyun-local-internal-secret

AI_ENABLED=false
AI_PROVIDER=deepseek
AI_BASE_URL=https://api.deepseek.com
DEEPSEEK_API_KEY=
AI_MODEL=deepseek-chat

CHROMA_URL=http://127.0.0.1:8000
CHROMA_COLLECTION=campus_maintenance_kb
EMBEDDING_MODEL_PATH=local-models/paraphrase-multilingual-MiniLM-L12-v2/model.onnx
EMBEDDING_TOKENIZER_PATH=local-models/paraphrase-multilingual-MiniLM-L12-v2/tokenizer.json
UPLOAD_DIRECTORY=./uploads
BACKUP_DIRECTORY=./backups
```

### 4. 一键启动，推荐

Windows 下运行：

```cmd
start-project.bat
```

或：

```powershell
.\start-project.ps1
```

脚本会完成以下工作：

- 读取根目录 `.env`
- 检查 MySQL `3306` 和 Nacos `8848`
- 使用 `local-cache/maven` 作为本地 Maven 仓库
- 编译后端共享模块
- 按顺序启动 user、repair、ops、ai、biz、gateway
- 启动前端开发服务
- 打开 `http://localhost:5173/`

### 5. 手动启动，备用

后端：

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

前端：

```bash
cd smart-frontend
npm install
npm run dev
```

访问地址：

```text
前端:   http://localhost:5173/
网关:   http://localhost:8070/
Nacos:  http://localhost:8848/nacos/
```

## 演示账号

导入 `full_init_test_data.sql` 后可使用以下账号登录。默认密码均为 `123456`。

| 角色 | 账号 | 说明 |
| --- | --- | --- |
| 管理员 | `admin` | 系统管理员 |
| 管理员 | `admin02` | 值班管理员 |
| 维修工 | `worker001` | 维修员张三 |
| 维修工 | `worker002` | 维修员李四 |
| 维修工 | `worker003` | 维修员赵六 |
| 维修工 | `worker004` | 维修员钱七 |
| 学生 | `20260001` | 学生王五 |
| 学生 | `20260002` | 学生账号 |
| 学生 | `20260003` 到 `20260008` | 其他演示学生 |

登录页提供管理员、维修工、学生三个快捷填充按钮，便于现场演示快速切换角色。

## 前端环境变量

`smart-frontend/.env.example`：

```env
VITE_API_BASE_URL=http://localhost:8070/api
VITE_WS_URL=ws://localhost:8070/ws
```

开发环境通常可直接使用默认值。若网关地址变化，复制为 `smart-frontend/.env.local` 后修改。

## AI 与 RAG 说明

- `AI_ENABLED=false` 或未配置 `DEEPSEEK_API_KEY` 时，系统使用本地规则引擎完成工单识别、紧急程度判断、建议生成和反馈情感分析。
- 配置 `AI_ENABLED=true` 且填写 `DEEPSEEK_API_KEY` 后，AI 服务会优先调用 DeepSeek/OpenAI-Compatible 接口，失败时自动降级到规则引擎。
- RAG 知识问答依赖 Chroma 和本地 Embedding 模型。若 Chroma 或模型不可用，系统仍可通过内置知识和明确降级提示保证主业务可用。

可选启动 Chroma：

```bash
docker run -d -p 8000:8000 chromadb/chroma:0.5.20
```

## 测试与构建

后端全量测试：

```bash
cd smart-backend
mvn test
```

指定模块测试示例：

```bash
cd smart-backend
mvn -pl qiyun-user-service -Dtest=UserProfileApiTests test
```

前端构建：

```bash
cd smart-frontend
npm run build
```

前端代码检查：

```bash
cd smart-frontend
npm run lint
```

Postman 集合位于 `postman/`：

- `Campus_Maintenance_API.postman_collection.json`
- `Campus_Maintenance_Local.postman_environment.json`
- `postman/README.md`

## 数据与文件目录

- 初始化 SQL：`smart-backend/qiyun-biz-service/src/main/resources/full_init_test_data.sql`
- 数据库名：`repairdb`
- 上传图片目录：默认 `./uploads`，可通过 `UPLOAD_DIRECTORY` 覆盖
- 备份目录：默认 `./backups`，可通过 `BACKUP_DIRECTORY` 覆盖
- 本地 Maven 缓存：`local-cache/maven`
- 本地向量模型：`local-models/paraphrase-multilingual-MiniLM-L12-v2/`

## 安全说明

- 不要提交真实 `.env`、数据库密码、JWT Secret、Internal Service Secret 或 API Key。
- `JWT_SECRET` 必须在 user、repair、ops、biz 等需要鉴权的服务之间保持一致。
- `INTERNAL_SERVICE_SECRET` 用于内部 Feign/内部接口调用鉴权，本地演示可使用固定值，正式环境必须更换。
- 管理员重置密码会返回临时密码，仅用于演示和本地开发场景。

## 版本状态

本项目当前作为毕业实习/课程演示最终版维护，核心功能链路已经完成：

- 三角色门户完整可用
- 工单闭环完整可用
- 网关、Nacos、JWT、Feign、MySQL 集成完整
- AI 智能识别和规则降级完整可用
- 统计分析、公告、通知、知识库、备份、审计、系统配置完整可用

如需继续扩展，建议优先围绕移动端适配、生产级密钥管理、AI 模型配置中心和更细粒度的消息推送策略进行增强。
