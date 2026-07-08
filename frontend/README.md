# 校园报修管理系统前端

基于 React + Vite + Ant Design 的校园报修管理系统前端应用。

## 技术栈

- **React 19** - 前端框架
- **Vite** - 构建工具
- **Ant Design 5** - UI 组件库
- **React Router** - 路由管理
- **Ant Design Charts** - 数据可视化

## 项目结构

```
frontend/
├── src/
│   ├── Admin/           # 管理员模块
│   │   ├── adminhome.jsx        # 管理员主页
│   │   ├── DataAnalysis.jsx     # 数据分析
│   │   ├── FeedbackManagement.jsx # 反馈管理
│   │   ├── RepairOrderList.jsx  # 工单列表
│   │   ├── UserManagement.jsx   # 用户管理
│   │   ├── backupService.jsx    # 备份服务
│   │   ├── feedbackService.jsx  # 反馈服务
│   │   ├── statisticsService.jsx # 统计服务
│   │   └── userService.jsx      # 用户服务
│   ├── Student/         # 学生模块
│   │   ├── stuhome.jsx          # 学生主页
│   │   ├── CreateRepairPage.jsx # 创建报修单
│   │   └── MyRepairs.jsx        # 我的报修单
│   ├── Worker/          # 维修工模块
│   │   ├── workerhome.jsx       # 维修工主页
│   │   ├── myTask.jsx           # 我的任务
│   │   └── mytaskService.jsx    # 任务服务
│   ├── services/        # API 服务
│   │   ├── api.jsx              # API 配置和请求封装
│   │   ├── authService.jsx      # 认证服务
│   │   ├── repairService.jsx    # 报修服务
│   │   └── PersonalInfoEd.jsx   # 个人信息编辑
│   ├── assets/          # 静态资源
│   ├── Login.jsx        # 登录页面
│   ├── Login.css        # 登录页样式
│   ├── app.jsx          # 主应用组件
│   ├── app.css          # 应用样式
│   └── main.jsx         # 应用入口
├── public/              # 公共静态资源
├── index.html           # HTML 入口文件
├── package.json         # 依赖配置
├── vite.config.js       # Vite 配置
└── eslint.config.js     # ESLint 配置
```

## 功能特性

### 学生端
- 提交报修申请
- AI 智能填写报修单
- 相似历史工单和维修知识库提示
- 查看我的报修单
- 上传报修图片
- 评价维修服务

### 维修工端
- 查看分配的任务
- 开始/完成维修任务
- 添加维修记录

### 管理员端
- 用户管理（学生、维修工）
- 工单分配与驳回
- 数据统计分析
- 校园设施健康指数和热点问题分析
- 智能运维中心：维修知识库、系统配置、审计日志
- DeepSeek AI 服务状态面板，可查看 AI 启用状态和使用场景
- 维修知识库支持 AI 生成草稿后人工确认保存
- 系统备份与恢复
- 反馈管理

## 开发指南

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

开发服务器运行在 http://localhost:3000，API 请求会自动代理到后端 http://localhost:8080。

### 测试账号

后端使用 `backend/src/main/resources/full_init_test_data.sql` 初始化后，可用以下账号登录前端，密码统一为 `123456`：

- 管理员：`admin`、`admin02`
- 维修员：`worker001`、`worker002`、`worker003`、`worker004`
- 学生：`20260001`、`20260002`、`20260003`、`20260004`、`20260005`、`20260006`、`20260007`、`20260008`

登录页内置管理员、维修员、学生三个快捷填充按钮，点击后会自动填入对应账号和统一密码，便于课堂演示。

### 第八步界面增强

- 个人中心新增“修改密码”页签。
- 学生评价弹窗支持速度、质量、态度、是否解决、匿名展示。
- 管理员 `智能运维中心` 新增转派审核页签，可审批维修人员转派申请。
- 数据分析页的备份恢复区展示备份目录、保留天数、备份数量和数据库名；恢复前后端会自动创建保护备份。
- 管理员系统配置可维护 AI、SLA 和上传限制等运行参数。
- 维修人员任务详情中的过程记录支持到场确认、耗材记录、维修记录和转派申请。
- 生产构建会屏蔽调试用 `console.log/debug`，保留警告和错误信息。

### 构建生产版本

```bash
npm run build
```

构建产物会自动输出到 `../backend/src/main/resources/static/` 目录，与 Spring Boot 后端集成。

### 代码检查

```bash
npm run lint
```

## API 配置

前端 API 基础地址默认是 `http://localhost:8080/api`，可在构建或开发启动前通过 `VITE_API_BASE_URL` 覆盖：

```powershell
$env:VITE_API_BASE_URL="http://localhost:8080/api"
```

开发环境下通过 Vite proxy 自动代理，生产环境下直接访问同源后端。

## 与后端集成

### 自动集成（推荐）

前端构建产物会自动输出到后端静态资源目录：

```
frontend/build/ → backend/src/main/resources/static/
```

运行后端即可访问前端应用：

```bash
cd ../backend
mvn spring-boot:run
```

访问 http://localhost:8080 即可使用完整应用。

### 手动集成

如需手动复制：

```bash
npm run build
cp -r build/* ../backend/src/main/resources/static/
```

## 注意事项

1. **Node.js 版本**: 推荐 Node.js 18+ 
2. **依赖安装**: 首次运行前请确保安装所有依赖
3. **后端服务**: 开发时需确保后端服务运行在 8080 端口
4. **数据库**: 后端需连接 MySQL 数据库

## 相关文档

- [React 文档](https://react.dev/)
- [Vite 文档](https://vitejs.dev/)
- [Ant Design 文档](https://ant.design/)
- [项目总体说明](../README.md)
- [后端说明](../backend/README.md)
