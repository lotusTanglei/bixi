# Bixi (基于 JDK 17 的企业级微服务架构)

## 📖 介绍
Bixi 是一个基于全新技术栈构建的企业级开发脚手架，旨在解决原方案前后端技术老旧的问题。
本项目基于 **JDK 17**、**Spring Boot 3.x**、**Spring Cloud Alibaba** 以及 **Vue 3** + **Element Plus** 进行深度重构，提供了一套完整、现代化的微服务/单体双架构兼容解决方案。

项目内置了权限管理、工作流引擎、AI集成、代码生成、OSS存储等丰富的基础能力，开箱即用，极大地降低了企业级应用的开发成本。

## 🏗️ 软件架构
本项目采用多模块（Multi-Module）架构设计，核心结构如下：

*   **bixi-common**: 全局通用组件库（包含核心工具、数据源、安全认证、OSS、工作流扩展、AI、MQ等）。
*   **bixi-gateway**: 基于 Spring Cloud Gateway 的微服务网关。
*   **bixi-auth**: 基于 OAuth2.1 的统一认证授权中心。
*   **bixi-module**: 核心业务模块集合：
    *   `bixi-upms-biz/api`: 统一权限管理子系统（用户、角色、菜单、部门、字典等）。
    *   `bixi-workflow-biz/api`: 高级工作流子系统（基于 Flowable 深度定制，支持中国式审批、加签、驳回等）。
    *   `bixi-ai-biz/api`: AI 智能集成子系统。
    *   `bixi-quartz`: 分时调度任务子系统。
    *   `bixi-monitor`: 系统监控服务。
    *   `bixi-generator`: 可视化代码生成器。
*   **bixi-single**: 单体部署模式入口（如果不需要微服务，可以直接启动此模块运行全部功能）。
*   **bixi-ui**: 基于 Vue 3 + TypeScript + Vite + Element Plus 的前端管理控制台。

## 🚀 核心特性
- **双模架构**：支持微服务集群部署（Gateway + Auth + 业务模块），也支持单体一键启动（Single 模块）。
- **现代化技术栈**：全面拥抱 Java 17、Spring Boot 3、Vue 3 和 Vite。
- **强大的工作流引擎**：深度集成 Flowable，提供可视化的 BPMN 2.0 流程设计器，并专门针对国内复杂的审批流（如转办、委派、自由驳回）进行了封装。
- **完善的权限体系**：基于 Spring Security 和 OAuth2.1，支持细粒度的接口和按钮级权限控制。
- **统一存储抽象**：内置 `bixi-common-oss`，基于 S3 协议，可无缝切换 Minio、阿里云 OSS 或腾讯云 COS。
- **规范的 CI/CD**：内置了标准的 `Makefile` 和 `.gitlab-ci.yml` 门禁脚本，支持全链路一键编译、测试与打包。

## 🛠️ 安装教程

### 1. 环境准备
- JDK: `17+`
- Node.js: `18+`
- Maven: `3.8+`
- 数据库: `MySQL 8.0+`
- 缓存: `Redis 6.0+`
- (可选) 配置中心: `Nacos 2.5.x+`

### 2. 后端启动
本项目提供了便捷的 `Makefile` 进行环境构建和启动。
```bash
# 1. 在根目录执行全量编译
make backend-ci

# 2. 如果使用微服务模式，请依次启动 Nacos、bixi-gateway、bixi-auth 和其他所需模块
# 如果使用单体模式，直接启动 bixi-single 模块即可
```

### 3. 前端启动
```bash
cd bixi-ui
# 安装依赖
npm install
# 启动开发服务器
npm run dev
```

## 🤝 参与贡献
本项目采用标准的 **Git Flow** 工作流进行协作开发：
- `main` 分支：生产环境稳定版本，仅接受来自 `develop` 的合并。
- `develop` 分支：日常开发主分支，包含最新的功能集成。
- `feature/*` 分支：新功能开发分支，基于 `develop` 创建，开发完成后合并回 `develop`。

参与步骤：
1. Fork 本仓库
2. 基于 `develop` 分支新建 `feature/xxx` 分支
3. 提交代码 (`git commit -m "feat: xxx"`)
4. 新建 Pull Request 到原仓库的 `develop` 分支

## 📄 许可证
[MIT License](LICENSE)