# 校园智慧维修平台 Postman API 集合

## 概述

本目录包含启云校园智慧维修平台后端API的Postman集合和环境配置文件，用于接口测试和开发调试。

## 文件清单

| 文件 | 说明 |
|------|------|
| `Campus_Maintenance_API.postman_collection.json` | 完整API集合（78个接口） |
| `Campus_Maintenance_Local.postman_environment.json` | 本地环境变量配置 |
| `README.md` | 本使用说明 |

## 导入步骤

### 1. 导入集合

1. 打开 Postman
2. 点击左上角 `Import` 按钮
3. 选择 `File` 标签
4. 拖入或选择 `Campus_Maintenance_API.postman_collection.json`
5. 点击 `Import` 确认

### 2. 导入环境

1. 点击右上角齿轮图标 → `Manage Environments`
2. 点击 `Import` 按钮
3. 选择 `Campus_Maintenance_Local.postman_environment.json`
4. 点击 `Import` 确认

### 3. 激活环境

在 Postman 右上角下拉框选择 `校园维修平台-本地环境`

## 环境变量说明

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `baseUrl` | 网关地址 | `http://localhost:8070` |
| `studentToken` | 学生Token（自动填充） | 登录后自动保存 |
| `staffToken` | 维修工Token（需手动填充） | 登录后自动保存 |
| `adminToken` | 管理员Token（自动填充） | 登录后自动保存 |
| `ticketId` | 工单ID（自动填充） | 创建工单后自动保存 |

## 接口分组说明

### 01-Auth与用户（6个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/auth/register` | POST | 公开 | 用户注册（仅学生） |
| `/api/auth/login` | POST | 公开 | 用户登录，自动保存Token |
| `/api/users/me` | GET | 认证 | 获取当前用户信息 |
| `/api/users/me` | PUT | 认证 | 更新用户信息 |
| `/api/users/me/password` | PUT | 认证 | 修改密码 |

### 02-报修工单（5个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/repair-orders` | POST | STUDENT | 创建报修工单 |
| `/api/repair-orders/my` | GET | STUDENT | 获取我的工单列表 |
| `/api/repair-orders/{id}` | GET | 认证 | 获取工单详情 |
| `/api/repair-orders/{id}` | DELETE | STUDENT | 删除工单 |
| `/api/repair-orders/{id}/evaluate` | POST | STUDENT | 评价工单 |

### 03-维修工任务（7个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/tasks/my` | GET | STAFF | 获取我的任务列表 |
| `/api/staff/dashboard` | GET | STAFF | 获取工作台统计 |
| `/api/tasks/{id}/accept` | POST | STAFF | 接单 |
| `/api/tasks/{id}/arrive` | PUT | STAFF | 到场确认 |
| `/api/tasks/{id}/process-records` | POST | STAFF | 添加维修记录 |
| `/api/tasks/{id}/resolve` | PUT | STAFF | 完成工单 |
| `/api/tasks/{id}/transfer-request` | POST | STAFF | 申请转派 |

### 04-分类与上传（5个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/categories` | GET | 公开 | 获取分类列表 |
| `/api/upload/images` | POST | 认证 | 上传图片 |
| `/api/categories` | POST | ADMIN | 新增分类 |
| `/api/categories/{id}` | PUT | ADMIN | 更新分类 |
| `/api/categories/{id}` | DELETE | ADMIN | 删除分类 |

### 05-通知与知识库（6个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/notifications` | GET | 认证 | 获取通知列表 |
| `/api/notifications/unread-count` | GET | 认证 | 获取未读数量 |
| `/api/notifications/{id}/read` | PUT | 认证 | 标记已读 |
| `/api/notifications/read-all` | PUT | 认证 | 全部标记已读 |
| `/api/knowledge-base/search` | GET | 认证 | 搜索知识库 |
| `/api/knowledge-base/recommend` | GET | 认证 | 获取知识推荐 |

### 06-管理员用户管理（5个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/admin/users` | GET | ADMIN | 分页获取用户列表 |
| `/api/admin/users` | POST | ADMIN | 创建用户 |
| `/api/admin/users/{id}` | PUT | ADMIN | 更新用户信息 |
| `/api/admin/users/{id}/reset-password` | POST | ADMIN | 重置密码 |
| `/api/admin/users/{id}` | DELETE | ADMIN | 禁用用户 |

### 07-管理员工单管理（7个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/admin/repair-orders` | GET | ADMIN | 分页获取所有工单 |
| `/api/admin/repair-orders/{id}/assign` | PUT | ADMIN | 分配工单 |
| `/api/admin/repair-orders/{id}/recommend-staff` | GET | ADMIN | 推荐维修工 |
| `/api/admin/repair-orders/{id}/status` | PUT | ADMIN | 更新状态 |
| `/api/admin/repair-orders/{id}/repair-notes` | PUT | ADMIN | 更新维修备注 |
| `/api/admin/repair-orders/{id}/process-notes` | PUT | ADMIN | 更新处理备注 |
| `/api/admin/repair-orders/{id}/estimated-completion-time` | PUT | ADMIN | 设置预计完成时间 |

### 08-统计评价与备份（15个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/admin/stats/status` | GET | ADMIN | 状态统计 |
| `/api/admin/stats/category` | GET | ADMIN | 分类统计 |
| `/api/admin/stats/location` | GET | ADMIN | 地点统计 |
| `/api/admin/stats/monthly` | GET | ADMIN | 月度趋势 |
| `/api/admin/stats/repairman-rating` | GET | ADMIN | 维修工评分 |
| `/api/admin/stats/processing-time` | GET | ADMIN | 平均处理时间 |
| `/api/admin/stats/sla` | GET | ADMIN | SLA概览 |
| `/api/admin/stats/hotspot` | GET | ADMIN | 热点分析 |
| `/api/admin/stats/facility-health` | GET | ADMIN | 设施健康 |
| `/api/admin/feedbacks` | GET | ADMIN | 获取评价列表 |
| `/api/admin/backup/create` | POST | ADMIN | 创建备份 |
| `/api/admin/backup/list` | GET | ADMIN | 获取备份列表 |
| `/api/admin/backup/status` | GET | ADMIN | 获取备份状态 |
| `/api/admin/backup/restore` | POST | ADMIN | 恢复数据库 |
| `/api/admin/backup/{fileName}` | DELETE | ADMIN | 删除备份 |

### 09-管理员知识库管理（4个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/admin/knowledge-base` | GET | ADMIN | 获取知识库列表 |
| `/api/admin/knowledge-base` | POST | ADMIN | 新增知识条目 |
| `/api/admin/knowledge-base/{id}` | PUT | ADMIN | 更新知识条目 |
| `/api/admin/knowledge-base/{id}` | DELETE | ADMIN | 删除知识条目 |

### 10-AI接口（2个接口）

| 接口 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/api/ai/analyze-ticket` | POST | 认证 | 智能分析报修单 |
| `/api/ai/health` | GET | 公开 | AI服务健康检查 |

## 测试账号

| 用户ID | 密码 | 角色 |
|--------|------|------|
| `admin` | `123456` | 管理员 |
| `teststudent` | `Test@123` | 学生（需注册） |

## 自动化测试脚本

登录接口包含测试脚本，会自动保存Token：

```javascript
// 学生登录后自动保存Token
if (pm.response.code === 200) {
    const response = pm.response.json();
    if (response.token) {
        pm.collectionVariables.set('studentToken', response.token);
    }
}
```

创建工单后会自动保存工单ID：

```javascript
// 创建工单后自动保存ticketId
if (pm.response.code === 201) {
    const response = pm.response.json();
    if (response.data && response.data.ticketId) {
        pm.collectionVariables.set('ticketId', response.data.ticketId);
    }
}
```

## 使用流程建议

### 首次测试流程

1. 执行「学生登录」保存 `studentToken`
2. 执行「管理员登录」保存 `adminToken`
3. 执行「创建报修工单」保存 `ticketId`
4. 执行其他需要认证的接口

### 注意事项

1. **Token有效期**: 默认24小时，过期需重新登录
2. **工单状态**: 只有「待接单」状态的工单可删除
3. **评价权限**: 只有「待评价」状态的工单可评价
4. **管理员创建用户**: 可创建STAFF和ADMIN角色

## 故障排查

| 问题 | 可能原因 | 解决方案 |
|------|----------|----------|
| 401 Unauthorized | Token过期或无效 | 重新执行登录接口 |
| 403 Forbidden | 角色权限不足 | 使用对应角色的Token |
| 404 Not Found | 工单不存在或已删除 | 检查ticketId是否正确 |
| 连接超时 | 服务未启动 | 检查Gateway和各服务状态 |

## 更新日志

- 2026-07-16: 初始版本，包含78个接口，按模块分组