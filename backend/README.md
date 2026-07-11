# 报修系统数据库设计说明

## 1. 数据库结构

### 1.1 核心实体表

#### sys_user (用户表)
- **主键**: user_number (VARCHAR(255))
- **字段**:
    - name: 用户姓名 (VARCHAR(103), NOT NULL)
    - password: 加密密码 (VARCHAR(255), NOT NULL)
    - role: 角色 (VARCHAR(20), NOT NULL, ENUM('student', 'staff', 'admin'))
    - enabled: 是否激活 (BOOLEAN, DEFAULT TRUE)
    - phone: 联系电话 (VARCHAR(20))
    - created_at: 创建时间 (DATETIME, NOT NULL)

#### repair_category (报修分类表)
- **主键**: id (BIGINT AUTO_INCREMENT)
- **字段**:
    - category_key: 分类标识 (VARCHAR(50), NOT NULL, UNIQUE)

#### repair_order (报修单表)
- **主键**: id (BIGINT AUTO_INCREMENT)
- **外键**:
    - student_number → sys_user.user_number
    - category_key → repair_category.category_key
    - repairman_id → sys_user.user_number
- **字段**:
    - status: 状态 (VARCHAR(30), NOT NULL)
    - location: 地点描述 (VARCHAR(100), NOT NULL)
    - description: 问题描述 (TEXT, NOT NULL)
    - rejection_reason: 驳回理由 (TEXT)
    - priority: 优先级 (VARCHAR(10), DEFAULT 'medium')
    - repair_notes: 维修记录 (TEXT)
    - process_notes: 处理过程 (TEXT)
    - estimated_completion_time: 预计完成时间 (DATETIME)
    - created_at: 创建时间 (DATETIME, NOT NULL)
    - assigned_at: 分配时间 (DATETIME)
    - completed_at: 完成时间 (DATETIME)
    - closed_at: 关闭时间 (DATETIME)

#### repair_order_image (报修图片表)
- **主键**: id (BIGINT AUTO_INCREMENT)
- **外键**: repair_order_id → repair_order.id
- **字段**:
    - image_url: 图片URL (VARCHAR(500), NOT NULL)
    - created_at: 创建时间 (DATETIME, NOT NULL)

#### repair_order_status_log (工单日志表)
- **主键**: id (BIGINT AUTO_INCREMENT)
- **外键**:
    - repair_order_id → repair_order.id
    - operate_user_id → sys_user.user_number
- **字段**:
    - before_status: 变更前状态 (VARCHAR(30))
    - after_status: 变更后状态 (VARCHAR(30), NOT NULL)
    - operate_time: 操作时间 (DATETIME, NOT NULL)

#### repair_feedback (服务评价表)
- **主键**: id (BIGINT AUTO_INCREMENT)
- **外键**:
    - repair_order_id → repair_order.id
    - student_number → sys_user.user_number
    - repairman_id → sys_user.user_number
- **字段**:
    - rating: 评分 (INT, NOT NULL, CHECK(rating >= 1 AND rating <= 5))
    - comment: 评论 (TEXT)
    - created_at: 创建时间 (DATETIME, NOT NULL)

#### V3 智能特色与运维表
- **sys_audit_log**: 管理员关键操作审计日志
- **sys_system_config**: 系统配置中心，保存 AI、SLA、上传限制和备份等配置
- **repair_knowledge_base**: 维修知识库，保存常见问题、处理步骤、安全注意事项
- **ai_ticket_analysis**: AI 智能报修分析缓存

### 1.2 实体关系
- Categories (1) → Repair_Tickets (N): 一个分类可以对应多张报修单
- Users (1) → Repair_Tickets (N): 一个用户可以提交多张报修单
- Users (1) → Repair_Tickets (N): 一个维修工可以处理多张报修单
- Repair_Tickets (1) → Ticket_ Images (N): 一张报修单可以有多张图片
- Repair_Tickets (1) → Ticket_Status_Logs (N): 一张报修单在生命周期中会产生多条状态变更日志
- Users (1) → Ticket_Status_Logs (N): 一个用户可以执行多次操作
- Repair_Tickets (1) → Ratings (N): 一张报修单可以有评价

## 2. DTO层设计

### 2.1 请求DTO (request/)
- **CategoryRequest**: 用于创建/更新分类的请求参数
- **LoginRequest**: 用于登录的请求参数
- **ResetPasswordRequest**: 用于重置密码的请求参数
- **TicketAssignRequest**: 用于分配报修单的请求参数
- **TicketCreateRequest**: 用于创建报修单的请求参数
- **TicketImageRequest**: 用于上传图片的请求参数
- **TicketRatingRequest**: 用于评价报修单的请求参数
- **TicketStatusUpdateRequest**: 用于更新报修单状态的请求参数
- **UserRegisterRequest**: 用于用户注册的请求参数
- **UserUpdateRequest**: 用于更新用户信息的请求参数

### 2.2 响应DTO (response/)
- **AuthResponse**: 认证成功的响应数据
- **ResetPasswordResult**: 重置密码的结果

### 2.3 数据传输对象 (dto/)
- **CategoryDto**: 传输分类信息
- **TicketDetailDto**: 传输报修单详细信息
- **TicketSummaryDto**: 传输报修单摘要信息
- **UserDto**: 传输用户信息
- **PagedResult**: 传输分页结果
- **RatingDto**: 传输评价信息
- **RepairmanRatingStatsDto**: 传输维修工评价统计信息
- **LocationStatsDto**: 传输地点统计信息
- **TicketImageDto**: 传输图片信息
- **TicketStatusLogDto**: 传输状态日志信息
- **AiTicketAnalysisDto**: 传输智能报修识别结果、相似工单和知识库推荐
- **SimilarTicketDto**: 传输相似历史工单
- **KnowledgeBaseDto**: 传输维修知识库条目
- **AuditLogDto**: 传输审计日志
- **SystemConfigDto**: 传输系统配置

## 3. 数据库初始化

数据库初始化脚本位于 `src/main/resources/`：

- `full_init_test_data.sql`: 推荐用于本地演示和功能测试，一键创建 `repairdb`、重建项目表、插入完整测试数据与各状态工单。
- `schema.sql`: 基础建表和少量初始数据脚本。

本地完整初始化：

```sql
SOURCE E:/Software_System_Design_and_Development_Practice/Campus-Maintenance-System/backend/src/main/resources/full_init_test_data.sql;
```

`full_init_test_data.sql` 会清空并重建项目相关表，重复执行前请确认不需要保留现有数据。

预置测试账号密码统一为 `123456`：

- 管理员：`admin`、`admin02`
- 维修员：`worker001`、`worker002`、`worker003`、`worker004`
- 学生：`20260001`、`20260002`、`20260003`、`20260004`、`20260005`、`20260006`、`20260007`、`20260008`

## 4. 智能特色接口

- `POST /api/ai/ticket/analyze`: 智能识别报修描述，返回标题、分类、位置、优先级、安全提醒、相似工单和知识库建议。
- `POST /api/ai/ticket/similar`: 根据描述、位置、分类检索相似历史工单。
- `GET /api/ai/ticket/{ticketId}/summary`: 生成管理员/维修人员可读的工单摘要。
- `POST /api/ai/repair-report/generate`: 辅助维修人员生成标准维修报告。
- `POST /api/ai/knowledge/draft`: 管理员输入分类和故障现象后生成维修知识库草稿。
- `GET /api/admin/stats/facility-health`: 校园设施健康指数。
- `GET /api/admin/knowledge-base`: 管理员查询维修知识库。
- `GET /api/admin/system-config`: 管理员查询系统配置。
- `GET /api/admin/audit-logs`: 管理员查询操作审计日志。
- `GET /api/admin/transfer-requests`: 管理员查询维修转派申请。
- `PUT /api/admin/transfer-requests/{recordId}/decision`: 管理员审批转派申请。
- `PUT /api/tasks/{id}/arrive`: 维修人员到场确认。
- `POST /api/tasks/{id}/process-records`: 维修人员新增过程记录。
- `POST /api/tasks/{id}/transfer-request`: 维修人员提交转派申请。
- `PUT /api/users/me/password`: 当前登录用户修改密码。

未配置外部大模型时，后端默认使用本地规则引擎和关键词相似度，保证本地演示不依赖网络。

系统配置中心支持维护 `ai.enabled`、`ai.api-key`、`ai.model`、SLA 时限、图片上传数量和单张大小限制。`upload.max-image-count` 与 `upload.max-image-size-mb` 会被后端上传服务实时读取，默认分别为 5 张和 5MB。备份恢复前会自动创建保护备份；定时备份默认关闭，可通过 `backup.auto-enabled=true` 和 `backup.cron` 开启。

DeepSeek 启用方式：

```yaml
ai:
  enabled: true
  provider: deepseek
  base-url: https://api.deepseek.com
  api-key: ${DEEPSEEK_API_KEY:}
  model: ${DEEPSEEK_MODEL:deepseek-v4-flash}

jwt:
  secret: ${JWT_SECRET:}
  expiration-ms: ${JWT_EXPIRATION_MS:604800000}
```

启动后可在管理员端 `智能运维中心 -> DeepSeek AI 服务` 查看 AI 启用状态，并在 `维修知识库` 中使用 AI 生成知识条目草稿。

## 5. 文件夹结构说明

### src/main/java/com/ligong/reportingcenter/domain/entity/
- **作用**: 存放JPA实体类，与数据库表一一对应
- **职责**:
    - 定义业务实体的数据模型
    - 配置与数据库的映射关系
    - 实现业务逻辑的持久化操作

### src/main/java/com/ligong/reportingcenter/dto/
- **作用**: 存放数据传输对象
- **职责**:
    - **request/**: 定义API请求参数
    - **response/**: 定义API响应数据
    - **其他**: 定义内部数据传输对象

### src/main/java/com/ligong/reportingcenter/repository/
- **作用**: 存放数据访问接口
- **职责**:
    - 定义数据库操作方法
    - 提供CRUD操作
    - 实现复杂查询

### src/main/java/com/ligong/reportingcenter/service/
- **作用**: 存放业务逻辑服务
- **职责**:
    - 封装业务规则
    - 协调多个Repository的操作
    - 实现事务管理

### src/main/java/com/ligong/reportingcenter/controller/
- **作用**: 存放RESTful API控制器
- **职责**:
    - 接收HTTP请求
    - 调用Service层处理业务
    - 返回JSON响应

### src/main/java/com/ligong/reportingcenter/util/
- **作用**: 存放工具类
- **职责**:
    - 提供通用功能（如JWT处理）
    - 封装重复代码
    - 提供辅助方法

### src/main/resources/
- **作用**: 存放配置文件和资源
- **职责**:
    - **application.yml**: 应用配置
    - **schema.sql**: 数据库初始化脚本
    - **static/**: 静态资源
    - **templates/**: 模板文件

## 6. 开发规范

### 5.1 命名规范
- **包名**: 使用小写字母，用点分隔
- **类名**: 使用大驼峰命名法
- **方法名**: 使用小驼峰命名法
- **字段名**: 使用小驼峰命名法





