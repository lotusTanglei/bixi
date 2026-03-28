# Bixi - AI 辅助开发指南

基于 JDK 17 + Spring Boot 3 的企业级微服务架构脚手架，支持微服务/单体双模部署。

## 项目概述

**Bixi** (碧玺) 是一个现代化的企业级开发脚手架，采用最新的技术栈（JDK 17、Spring Boot 3.4、Vue 3.5），提供完整的微服务/单体双架构解决方案。

### 核心理念

- **双模架构**：Maven Profile 切换 cloud/single 模式，支持微服务集群和单体应用
- **现代化技术栈**：全面拥抱 Java 17、Spring Boot 3、Vue 3 Composition API
- **开箱即用**：内置权限管理、工作流、AI 集成、代码生成等企业级功能
- **安全可靠**：OAuth2.1 认证、细粒度权限控制、XSS 防护、审计日志

---

## 技术栈

### 后端技术栈
- **核心框架**：Spring Boot 3.4.1 + Spring Cloud Alibaba
- **开发语言**：JDK 17+
- **安全框架**：Spring Security 6.x + OAuth2.1
- **持久层**：MyBatis Plus 3.5.x + MySQL 8.0+
- **缓存**：Redis 6.0+
- **工作流**：Flowable 7.x (深度定制)
- **配置中心**：Nacos 2.5.x+

### 前端技术栈
- **核心框架**：Vue 3.5 + TypeScript 5.6
- **构建工具**：Vite 5.3
- **UI 组件**：Element Plus 2.8
- **状态管理**：Pinia 2.2
- **路由管理**：Vue Router 4.x

---

## 项目结构

```
bixi/
├── bixi-common/              # 通用组件库
│   ├── bixi-common-bom           # 依赖版本管理
│   ├── bixi-common-core          # 核心工具（R、异常、工具类）
│   ├── bixi-common-security      # 安全认证（OAuth2、JWT）
│   ├── bixi-common-mybatis       # MyBatis Plus 扩展（含BaseController、BaseService）
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
├── SECURITY.md               # 安全政策
├── CONTRIBUTING.md           # 贡献指南
├── README.md                 # 项目文档
└── pom.xml                  # 版本号: ${revision}
```

---

## 核心功能

### 1. 权限管理 (UPMS)
- Spring Security 细粒度权限控制
- 用户/角色/菜单/部门管理
- 数据权限、接口权限、按钮权限
- 在线用户监控、操作日志

### 2. 工作流引擎 (Workflow)
- Flowable 深度定制
- 支持中国式审批流（会签、驳回、转办、委派）
- 可视化流程设计器
- 表单管理、流程监控

### 3. AI 集成 (AI)
- 内置 AI 接口封装
- 对话历史管理
- 模型配置、Prompt 管理
- 支持 SSE 流式响应

### 4. 代码生成器 (Generator)
- 可视化代码生成
- 支持前后端代码一键生成
- 模板可配置
- 在线预览和下载

### 5. 对象存储 (OSS)
- S3 协议抽象
- 支持 Minio、阿里云 OSS、腾讯云 COS
- 无缝切换存储后端

### 6. 系统监控 (Monitor)
- Spring Boot Admin 集成
- 服务器监控、在线用户
- 健康检查、性能监控

---

## 重要约定

### Git Flow 工作流
- **main** 分支：生产环境稳定版本
- **develop** 分支：日常开发主分支
- **feature/*** 分支：新功能开发
- **fix/*** 分支：Bug 修复
- **hotfix/*** 分支：紧急修复

### 代码规范
- **后端**：遵循阿里巴巴 Java 开发手册（嵩山版）
- **前端**：遵循 Vue 3 风格指南，全面使用 Composition API
- **依赖注入**：统一使用 `@RequiredArgsConstructor`
- **提交规范**：feat/fix/docs/refactor/test/chore 前缀

### 版本管理
- 使用 `pom.xml` 中的 `${revision}` 统一管理版本号
- 当前版本：**0.0.3**
- 遵循语义化版本规范

---

## 关键实现

### 双模式切换

**微服务模式**：
```bash
mvn clean package -Pcloud
# 启动顺序：Nacos → Gateway → Auth → 业务模块
```

**单体模式**：
```bash
mvn clean package -Psingle
# 直接启动 bixi-single 模块
```

### 基类使用

项目提供了通用的 CRUD 基类，减少样板代码：

**Controller 基类**：
```java
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController extends BaseController<SysUser> {

    private final SysUserService userService;

    @Override
    protected BaseService<SysUserMapper, SysUser> getBaseService() {
        return userService;
    }

    // 添加自定义业务方法
}
```

**Service 基类**：
```java
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends BaseService<SysUserMapper, SysUser> implements SysUserService {
    // 继承后拥有所有基础 CRUD 方法
    // 只需实现特定的业务方法
}
```

### 工作流扩展

`bixi-common-workflow` 模块扩展了 Flowable，封装了：
- 会签（并行审批、串行审批）
- 驳回（驳回至发起人、驳回至上一节点）
- 转办、委派
- 撤回、终止

### 权限控制

**接口级权限**：
```java
@HasPermission("sys_user_add")
@PostMapping
public R save(@RequestBody SysUser user) {
    return R.ok(userService.save(user));
}
```

**按钮级权限**：前端通过 `v-hasPermission` 指令控制

---

## 代码审查结果 (2026-03-28)

项目已完成全面的代码审查，详见 [CODE_REVIEW_REPORT.md](CODE_REVIEW_REPORT.md)。

### 已完成的改进 ✅

1. **安全文档**：
   - ✅ 创建 [SECURITY.md](SECURITY.md)：包含安全政策、配置清单、漏洞报告流程
   - ✅ 创建 [CONTRIBUTING.md](CONTRIBUTING.md)：贡献指南、代码规范、开发流程

2. **配置规范**：
   - ✅ 创建 `.editorconfig`：统一编辑器配置
   - ✅ 创建 `.env.example`：环境变量模板

3. **代码质量**：
   - ✅ 创建 `BaseController` 和 `BaseService`：封装通用 CRUD 操作
   - ✅ 添加完备的 JavaDoc 注释
   - ✅ 更新 [README.md](README.md)：生成完备的项目文档

### 待完成的改进 ⏳

1. **前端 Vue 3 API 统一**：
   - 将 59 处 Options API 迁移到 Composition API (`<script setup>`)
   - 减少 `any` 类型使用，创建具体类型定义
   - 建议作为单独的重构 PR 执行

2. **代码重复消除**：
   - 提取前端通用表格组件
   - 统一异常处理体系
   - AOP 统一处理权限和日志

### 审查评分

**整体评分**：B+ (良好)

**优势**：
- ✅ 架构设计合理，模块化清晰
- ✅ 技术栈现代化，功能完整
- ✅ 适合学习和二次开发

**改进方向**：
- 📌 统一代码风格（降低新贡献者门槛）
- 📌 完善文档和示例（提升项目专业度）
- 📌 安全配置指引（保护项目用户）

---

## 开发指南

### 快速开始

1. **环境准备**：JDK 17+、Node.js 18+、Maven 3.8+、MySQL 8.0+、Redis 6.0+
2. **数据库初始化**：执行 `bixi-project-documents/sql/` 目录下的 SQL 脚本
3. **配置文件**：复制 `.env.example` 为 `.env` 并填写真实配置
4. **启动项目**：
   ```bash
   # 后端（单体模式）
   mvn clean install
   cd bixi-single
   mvn spring-boot:run

   # 前端
   cd bixi-ui
   npm install
   npm run dev
   ```

### AI 辅助开发

本项目支持 AI 辅助开发，建议：

1. **使用 Claude AI**：
   - 理解项目架构和代码结构
   - 生成代码片段和示例
   - 代码审查和优化建议

2. **推荐工具**：
   - IDE 集成 Claude 插件
   - 使用 Claude Code 进行代码重构
   - 依赖 AI 工具进行代码解释

3. **注意事项**：
   - AI 生成代码需要人工审查
   - 注意数据脱敏和安全
   - 遵循项目代码规范

### 常见问题

**Q1: 如何切换微服务/单体模式？**
A: 使用 Maven Profile：`mvn package -Pcloud` 或 `mvn package -Psingle`

**Q2: 如何自定义权限？**
A: 在 `@HasPermission` 注解中指定权限标识，在菜单管理中配置

**Q3: 如何扩展工作流？**
A: 继承 `bixi-common-workflow` 提供的基类，实现自定义业务逻辑

**Q4: 如何集成 AI 服务？**
A: 配置 `bixi-common-ai` 模块，实现 `AiService` 接口

---

## 相关文档

- [README.md](README.md) - 项目主页和快速开始
- [CONTRIBUTING.md](CONTRIBUTING.md) - 贡献指南和代码规范
- [SECURITY.md](SECURITY.md) - 安全政策和配置清单
- [CODE_REVIEW_REPORT.md](CODE_REVIEW_REPORT.md) - 代码审查报告
- [bixi-project-documents/sql/README.md](bixi-project-documents/sql/README.md) - 数据库文档

---

## 社区

- **GitHub**：https://github.com/lotus-bixi/bixi
- **邮箱**：dev@lotus-bixi.com
- **问题反馈**：[GitHub Issues](https://github.com/lotus-bixi/bixi/issues)

---

**最后更新**：2026-03-28
**当前版本**：0.0.3
**维护团队**：Lotus Bixi Team
