<div align="center">

# Bixi

### 基于 JDK 17 + Spring Boot 3 的企业级微服务架构脚手架

[![JDK](https://img.shields.io/badge/JDK-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/lotus-bixi/bixi?style=social)](https://github.com/lotus-bixi/bixi)

**现代化 · 企业级 · 双模架构 · 开箱即用**

[快速开始](#-快速开始) · [文档](#-文档) · [贡献指南](CONTRIBUTING.md) · [安全政策](SECURITY.md)

</div>

---

## 📖 项目介绍

> **Bixi** (碧玺) 是一种珍贵的宝石，寓意项目如宝石般璀璨、坚固、有价值。

Bixi 是一个**现代化的企业级开发脚手架**，基于 **JDK 17**、**Spring Boot 3.4**、**Spring Cloud Alibaba** 和 **Vue 3.5** 构建，旨在提供一套完整、优雅、易用的微服务/单体双架构解决方案。

### 💡 核心亮点

- **🎯 双模架构**：Maven Profile 一键切换微服务/单体模式
- **🚀 现代化技术栈**：全面拥抱 Java 17、Spring Boot 3、Vue 3 Composition API
- **🔐 完善的权限体系**：OAuth2.1 + JWT，支持接口级和按钮级权限控制
- **🌊 强大的工作流引擎**：基于 Flowable 深度定制，支持中国式审批流
- **🤖 AI 集成能力**：内置 AI 接口，快速接入大模型服务
- **📦 开箱即用**：内置代码生成器、OSS 存储、定时任务、系统监控
- **🛡️ 安全可靠**：XSS 防护、SQL 注入防护、数据脱敏、审计日志

### 🎯 适用场景

- ✅ 企业后台管理系统
- ✅ SaaS 平台开发
- ✅ 微服务架构项目
- ✅ 快速原型开发
- ✅ 学习参考和二次开发

---

## 🏗️ 技术栈

### 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 采用最新的 LTS 版本 |
| Spring Boot | 3.4.1 | 核心框架 |
| Spring Cloud Alibaba | 2022.x | 微服务组件 |
| Spring Security | 6.x | 安全框架 |
| OAuth2 | 2.1 | 认证授权 |
| MyBatis Plus | 3.5.x | ORM 框架 |
| Flowable | 7.x | 工作流引擎 |
| Redis | 6.0+ | 缓存中间件 |
| MySQL | 8.0+ | 关系型数据库 |
| Nacos | 2.5.x+ | 配置中心和服务发现 |

### 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5 | 前端框架 |
| TypeScript | 5.6 | 类型系统 |
| Vite | 5.3 | 构建工具 |
| Element Plus | 2.8 | UI 组件库 |
| Pinia | 2.2 | 状态管理 |
| Vue Router | 4.x | 路由管理 |

---

## 📂 项目结构

```
bixi/
├── bixi-common/              # 通用组件库
│   ├── bixi-common-bom           # 依赖版本管理
│   ├── bixi-common-core          # 核心工具（R、异常、工具类）
│   ├── bixi-common-security      # 安全认证（OAuth2、JWT）
│   ├── bixi-common-mybatis       # MyBatis Plus 扩展
│   ├── bixi-common-oss           # 对象存储抽象（S3 协议）
│   ├── bixi-common-workflow      # 工作流扩展（Flowable）
│   ├── bixi-common-ai            # AI 集成
│   └── ...                       # 其他通用组件
├── bixi-gateway/             # Gateway 网关
├── bixi-auth/                # OAuth2.1 认证中心
├── bixi-module/              # 业务模块
│   ├── bixi-upms-biz             # 权限管理（用户、角色、菜单、部门）
│   ├── bixi-workflow-biz         # 工作流管理
│   ├── bixi-ai-biz               # AI 集成
│   ├── bixi-generator            # 代码生成器
│   ├── bixi-quartz               # 定时任务
│   └── bixi-monitor              # 系统监控
├── bixi-single/              # 单体模式入口
├── bixi-ui/                  # Vue 3 前端
├── bixi-project-documents/   # 项目文档
│   └── sql/                      # 数据库脚本
├── Makefile                  # 构建脚本
├── CLAUDE.md                 # AI 辅助开发说明
├── CONTRIBUTING.md           # 贡献指南
├── SECURITY.md               # 安全政策
└── pom.xml                   # Maven 配置
```

---

## 🚀 快速开始

### 1. 环境准备

确保你的开发环境满足以下要求：

```bash
# 检查 Java 版本（需要 17+）
java -version

# 检查 Node.js 版本（需要 18+）
node -v

# 检查 Maven 版本（需要 3.8+）
mvn -v
```

**必需组件**：
- MySQL 8.0+
- Redis 6.0+
- Nacos 2.5.x+ (微服务模式)

### 2. 克隆项目

```bash
git clone https://github.com/lotus-bixi/bixi.git
cd bixi
```

### 3. 数据库初始化

```bash
# 1. 创建数据库
mysql -u root -p
CREATE DATABASE bixi CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

# 2. 导入表结构
mysql -u root -p bixi < bixi-project-documents/sql/01_init_all_tables.sql

# 3. 导入初始数据（⚠️ 重要！系统需要基础数据才能运行）
mysql -u root -p bixi < bixi-project-documents/sql/04_init_data.sql

# 4. 可选：导入约束和索引优化
mysql -u root -p bixi < bixi-project-documents/sql/02_add_constraints.sql
mysql -u root -p bixi < bixi-project-documents/sql/03_add_indexes.sql
```

> ⚠️ **重要**：
> - **必须执行 `04_init_data.sql`**，否则系统无法正常使用！
> - 初始数据包含：默认管理员、角色、菜单、字典、代码生成器模板
> - 默认管理员账号：`admin` / `admin123`（首次登录后请立即修改）

### 4. 配置文件

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填写真实的配置信息
# 主要是数据库、Redis、Nacos 等连接信息
```

### 5. 启动项目

#### 方式一：单体模式（推荐快速体验）

```bash
# 编译项目
mvn clean install -DskipTests

# 启动单体应用
cd bixi-single
mvn spring-boot:run
```

#### 方式二：微服务模式

```bash
# 1. 启动 Nacos
cd nacos/bin
./startup.sh -m standalone

# 2. 启动网关
cd bixi-gateway
mvn spring-boot:run

# 3. 启动认证中心
cd bixi-auth
mvn spring-boot:run

# 4. 启动业务模块
cd bixi-module/bixi-upms-biz
mvn spring-boot:run
```

### 6. 启动前端

```bash
cd bixi-ui

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

访问：http://localhost:3000

**默认账号**：
- 用户名：`admin`
- 密码：`admin123`

---

## 🛠️ 使用 Makefile 快速构建

项目提供了便捷的 `Makefile` 进行环境构建和启动。

```bash
# 查看所有可用命令
make help

# 后端全量编译
make backend-ci

# 前端构建
make frontend-build

# 全量构建
make all

# 代码格式化
make format

# 运行测试
make test
```

---

## ⚙️ 配置说明

### Nacos 配置

微服务模式下，需要在 Nacos 中创建配置文件：

**命名空间**：`dev`（开发环境）

**配置列表**：
- `bixi-gateway.yml` - 网关配置
- `bixi-auth.yml` - 认证中心配置
- `bixi-upms-biz.yml` - 权限管理配置
- `bixi-workflow-biz.yml` - 工作流配置
- `bixi-ai-biz.yml` - AI 服务配置
- `bixi-common.yml` - 公共配置

详细配置说明请参考：[bixi-project-documents/sql/README.md](bixi-project-documents/sql/README.md)

### 数据源配置

```yaml
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/bixi?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: root
    password: your-password-here
```

### Redis 配置

```yaml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password: your-redis-password
    database: 0
```

---

## 📚 文档

- [数据库设计文档](bixi-project-documents/sql/DATA_DICTIONARY.md)
- [贡献指南](CONTRIBUTING.md)
- [安全政策](SECURITY.md)
- [AI 辅助开发说明](CLAUDE.md)

---

## 🎨 核心功能

### 1. 权限管理 (UPMS)

- ✅ 用户管理：用户增删改查、密码重置、用户导入导出
- ✅ 角色管理：角色权限分配、数据权限控制
- ✅ 菜单管理：动态菜单配置、按钮级权限控制
- ✅ 部门管理：组织架构树形管理
- ✅ 字典管理：系统字典配置
- ✅ 参数管理：系统参数配置
- ✅ 日志管理：操作日志、登录日志
- ✅ 在线用户：在线用户监控、强制下线

### 2. 工作流引擎 (Workflow)

- ✅ 流程设计器：可视化 BPMN 2.0 流程设计
- ✅ 流程管理：流程部署、挂起、激活
- ✅ 流程实例：流程发起、审批、撤回、驳回
- ✅ 任务管理：待办任务、已办任务、抄送任务
- ✅ 表单管理：业务表单配置
- ✅ 中国式审批：会签、或签、驳回、转办、委派

### 3. AI 集成 (AI)

- ✅ 对话管理：AI 对话历史记录
- ✅ 模型配置：多模型切换、参数配置
- ✅ 流式响应：支持 SSE 流式输出
- ✅ Prompt 管理：提示词模板管理

### 4. 代码生成器 (Generator)

- ✅ 表管理：数据库表导入
- ✅ 模板配置：代码生成模板
- ✅ 代码生成：一键生成前后端代码
- ✅ 预览下载：支持在线预览和打包下载

### 5. 定时任务 (Quartz)

- ✅ 任务管理：Cron 表达式配置
- ✅ 执行日志：任务执行历史
- ✅ 任务调度：手动触发、暂停、恢复

### 6. 系统监控 (Monitor)

- ✅ 在线用户：在线用户列表
- ✅ 服务器监控：CPU、内存、磁盘使用率
- ✅ 服务监控：服务状态、健康检查

---

## 🤝 参与贡献

我们欢迎任何形式的贡献！

### Git Flow 工作流

- **main** 分支：生产环境稳定版本
- **develop** 分支：日常开发主分支
- **feature/*** 分支：新功能开发
- **fix/*** 分支：Bug 修复
- **hotfix/*** 分支：紧急修复

### 贡献流程

1. Fork 本仓库
2. 基于 `develop` 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: 添加某个功能'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

详细贡献指南请参考：[CONTRIBUTING.md](CONTRIBUTING.md)

### 代码规范

- **后端**：遵循阿里巴巴 Java 开发手册（嵩山版）
- **前端**：遵循 Vue 3 风格指南
- **提交规范**：使用 Conventional Commits 规范

---

## 🔒 安全配置

⚠️ **生产环境部署前请务必**：

1. ✅ 修改默认管理员密码
2. ✅ 更新 JWT 密钥（使用强随机密钥）
3. ✅ 配置 HTTPS
4. ✅ 启用 API 速率限制
5. ✅ 检查 Nacos/Redis/MySQL 访问控制
6. ✅ 配置防火墙规则

详细安全配置清单请参考：[SECURITY.md](SECURITY.md)

---

## ❓ 常见问题

### 1. 启动时报错 "Connection refused"

检查 MySQL、Redis、Nacos 是否正常启动。

### 2. 前端访问后端接口跨域

检查 Gateway 或 Nginx 配置，确保 CORS 正确设置。

### 3. 登录后提示 "Token 已过期"

检查系统时间是否同步，检查 JWT 过期时间配置。

### 4. 代码生成器生成的代码无法编译

检查模板配置和数据库表结构是否符合规范。

更多问题请查阅 [Issue](https://github.com/lotus-bixi/bixi/issues)。

---

## 📞 联系我们

- 📧 邮箱：dev@lotus-bixi.com
- 💬 微信群：[扫码加入](docs/images/wechat-group.png)
- 🐛 问题反馈：[GitHub Issues](https://github.com/lotus-bixi/bixi/issues)
- 💡 功能建议：[GitHub Discussions](https://github.com/lotus-bixi/bixi/discussions)

---

## 🌟 鸣谢

感谢以下开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba)
- [MyBatis Plus](https://baomidou.com/)
- [Flowable](https://www.flowable.com/)
- [Vue](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)

---

## 📄 许可证

[MIT License](LICENSE)

Copyright (c) 2025 Lotus Bixi Team

---

<div align="center">

**如果这个项目对你有帮助，请给我们一个 ⭐Star**

Made with ❤️ by Lotus Bixi Team

</div>
