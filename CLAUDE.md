# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# Bixi - AI 辅助开发指南

> 本文档用于向 AI 编程助手提供 Bixi 项目的核心上下文。**保持精简，勿加入冗长说明**。

## 1. 项目核心定位
- **定位**：企业级微服务/单体双架构开发脚手架。
- **技术栈**：JDK 17+, Spring Boot 3.4.1, Spring Cloud Alibaba, Vue 3.5, TypeScript 5.6。
- **构建管理**：Maven Profile (`-Pcloud` 或 `-Psingle`) 切换部署模式。
- **核心中间件**：MySQL 8.0+, Redis 6.0+, Nacos 2.5.x, Flowable 7.x。

## 2. 模块结构（核心）
```text
bixi/
├── bixi-gateway/          # 微服务网关（路由、限流）
├── bixi-auth/             # OAuth2.1 + JWT 认证中心
├── bixi-module/           # 业务模块
│   ├── bixi-upms-*        # 权限管理（User/Role/Menu/Dept）
│   ├── bixi-workflow-*    # 工作流（Flowable 定制）
│   ├── bixi-ai-*          # AI 集成（大模型对话）
│   ├── bixi-generator/    # 代码生成器
│   └── bixi-quartz/       # 定时任务
├── bixi-common/           # 通用组件
│   ├── bixi-common-mybatis/   # BaseController/BaseService 基类
│   ├── bixi-common-security/  # 安全认证扩展
│   └── ...                    # 其他通用组件
├── bixi-single/           # 单体模式入口
└── bixi-ui/               # Vue 3 前端
```

## 3. 核心架构模式

### CRUD 基类继承
业务模块应继承通用基类获得标准 CRUD 能力：

**Controller 层**：继承 `BaseController<T>`
```java
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController extends BaseController<SysUser> {
    private final SysUserService userService;

    @Override
    protected BaseService<?, SysUser> getBaseService() {
        return userService;
    }
}
```
自动获得接口：`GET /{id}`, `GET /list`, `GET /page`, `POST /`, `PUT /`, `DELETE /{id}`, `DELETE /batch`

**Service 层**：实现 `BaseService<M, T>` 或继承 `IBaseService<T>`

### 双模架构切换
通过 Maven Profile 切换：
- `-Pcloud`（默认）：微服务模式，需启动 Nacos + Gateway + Auth + 业务模块
- `-Psingle`：单体模式，仅启动 `bixi-single` 模块

### 权限控制
- 接口级：`@HasPermission("sys:user:add")`
- 按钮级：`v-hasPermission="['sys:user:add']"`

## 4. AI 编码与重构公约
1. **优先修改现有文件**：绝对不要随意新建 `.md` 或其他文件，除非确有必要且用户同意。
2. **遵守基础规范**：
   - 后端：遵循阿里巴巴 Java 开发手册。优先复用 `BaseController` 和 `BaseService`。依赖注入统一用 `@RequiredArgsConstructor`。
   - 前端：强制使用 Vue 3 `Composition API` (`<script setup>`)，禁止使用 Options API，避免使用 `any`。
3. **安全与权限**：接口级权限基于 `@HasPermission("xxx")` 控制，按钮级基于 `v-hasPermission`。生成代码需包含鉴权逻辑。
4. **日志与审查**：生成的业务代码应包含恰当的异常处理与日志打印。提交前进行基础自测，确保可编译无严重 Bug。
5. **Git 规范**：使用 Conventional Commits (feat/fix/docs/refactor/chore 等前缀)。

## 5. 关键脚本与命令

### 后端
```bash
# 编译（默认 cloud 模式）
mvn clean install -DskipTests

# 单体模式编译
mvn clean install -DskipTests -Psingle

# 单体模式启动
cd bixi-single && mvn spring-boot:run

# 运行全部测试
mvn test

# 运行单个测试类
mvn test -Dtest=ChatServiceImplTest

# 代码格式化（Spring 规范）
mvn spring-javaformat:apply

# CI 构建（含测试）
make backend-ci
```

### 前端
```bash
cd bixi-ui

# 安装依赖
npm install

# 开发模式启动
npm run dev

# 构建各环境
npm run build:dev    # 开发环境
npm run build:test   # 测试环境
npm run build:prod   # 生产环境

# ESLint 检查与修复
npm run lint:eslint

# Prettier 格式化
npm run prettier
```

### 数据库
初始化脚本位于 `bixi-project-documents/sql/`：
1. `01_init_all_tables.sql` - 表结构
2. `04_init_data.sql` - **必须执行**（默认管理员: admin/admin123）