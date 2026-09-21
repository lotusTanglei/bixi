---
name: "bixi-feature"
description: "当用户要求在 Bixi 新增或扩展业务功能、CRUD、接口、后台页面、菜单或表结构，或为这些工作制定方案时使用。仅解释既有实现或修复已有故障不以此技能为主。"
---

# Bixi 功能开发

以一个完整业务链交付，cloud 与 single 共用实现。先确认用户要方案还是实施；“只出方案”止于修改清单、决策和验收计划，不创建业务文件。

## 项目入口与约定

从用户当前目录或已指定项目运行 `git rev-parse --show-toplevel`，只检查 `AGENTS.md`、`pom.xml`、`bixi-module/`、`bixi-ui/` 是否存在，此时不读取 POM 或业务源码。下述路径相对此 Bixi 根目录解析，不能从 Skill 安装目录找业务代码。先检查工作区状态并保留已有改动。

随后检查 `.codegraph/`；存在时，在读取 POM/Makefile/业务源码或用 Glob/Grep/rg 定位代码之前，先用 `codegraph explore "问题或符号"`（或同等 MCP 工具）；失败则记录错误后回退 `rg` 和定向读取。不存在则说明并直接跳过，不自动创建索引。已经返回的当前源码不重复读取。

读取 `AGENTS.md`、`.docs/1_ARCHITECTURE.md`、`.docs/5_AI_DEVELOPMENT.md` 及相关模块 README；以当前源码核实文档，不固化某版本的部署模块列表。用户指令与适用仓库约定优先；默认中文，跟随用户语言。单独安装即可工作，无需其他 Skill 或插件，不递归调用、不自动派生 Agent。

不得修改 `.docs/.chiwen.state.json`，不输出 `.env` 或凭据，不交付秘密、构建产物、个人绝对路径。已有授权有效，普通可逆工作不重复确认；Skill 调用不额外授权提交、合并、发布或破坏性操作。

<!-- bixi-ref: AGENTS.md -->
<!-- bixi-ref: .docs/1_ARCHITECTURE.md -->
<!-- bixi-ref: .docs/5_AI_DEVELOPMENT.md -->

## 实施流程

先输出目标、假设、预计修改文件与验证计划。已确认的设计直接实施；只补问无法推断且改变业务语义的问题。

- 模块归属：检查实际模块与组合根。依赖为 `deployment → biz → api/common`；公共模块和 API 不反向依赖 biz。业务实现只放一处，single 只组合；跨模块消费者只注入 `*-api/service` 契约，cloud 用 Feign 适配器，single 用本地适配器，禁止 localhost Feign 回环。
- 后端：以 `bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo` 为首阶段参考，完成 Entity、按需 DTO/VO、Mapper、Service、Controller、分页筛选与详情；Java 17、Jakarta Validation 和 `@Valid`。核对继承字段及逻辑删除，不让业务状态覆盖公共 `status`。不要为清单机械增加无用途类。
- 权限与审计：查询/详情 `@HasPermission("<domain>_<resource>_view")`；写入分别 `add/edit/del`。每个写操作加稳定标题的 `@SysLog`；前端 `v-auth` 与菜单 SQL 使用同名权限。
- SQL：表写入 `bixi-project-documents/sql/01_init_all_tables.sql`，过滤/排序索引写入 `03_add_indexes.sql`，菜单、按钮、角色关联及必要数据写入 `04_init_data.sql`。核对既有初始化顺序；持久环境另考虑迁移，不能为导入新表重置用户数据。
- 开发环境也不能默认清卷或执行 `make reset`；优先增量迁移或新建隔离项目。只有明确可丢弃且已获授权的环境才可重置，不能把删除数据列作一般开发的必需前置。
- 前端：参考 `bixi-ui/src/api/demo/task.ts` 与 `bixi-ui/src/views/demo/task`，复用请求、分页和消息工具；API 走 `/admin/...`，菜单驱动动态路由。完成列表、详情/表单、校验、按钮权限、加载/错误/空态与响应式布局。
- 生成器仅在模板存在且合适时采用，审查结果是否符合上述契约；模板不可用直接参考现有实现。

<!-- bixi-ref: bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo -->
<!-- bixi-ref: bixi-ui/src/api/demo/task.ts -->
<!-- bixi-ref: bixi-ui/src/views/demo/task -->
<!-- bixi-ref: bixi-project-documents/sql/01_init_all_tables.sql -->
<!-- bixi-ref: bixi-project-documents/sql/03_add_indexes.sql -->
<!-- bixi-ref: bixi-project-documents/sql/04_init_data.sql -->

## 验证与交付

编写聚焦业务的测试；行为变化扩展 `scripts/acceptance.mjs`，两种模式运行同一用例、满足相同业务断言，输出中的 `mode` 分别是 cloud 和 single。真实 CRUD、非法请求、无权限拒绝与有权限成功、三种写操作的持久化审计都要验证。仅反射检查注解或列出管理员权限不证明权限执行；异步日志用有界轮询核对本次请求标记。

运行受影响测试及 `make architecture-check`、`make runtime-config-check`、`make backend-cloud-ci`、`make backend-single-ci`、`make frontend-ci`。运行验收前核对 Java 17、Maven、Node、Docker/Compose、本地配置和端口；在自己的环境按 `make start-cloud` → `make verify-cloud` → `make start-single` → `make verify-single` 顺序执行，不并发切换模式、不停止其他任务服务。启动会打印初始凭据，原始启动日志保存到权限受限本地文件，只展示脱敏结果。

存在 `.codegraph/` 时执行 `codegraph sync .`，最后 `git diff --check`。只报告实际命令、工作目录、模式、退出码及证据；未执行/环境阻塞明确列出，不以构建通过代替运行验收。交付包含变更文件、验证结果、剩余风险；必需项未完成不能称完整交付。

<!-- bixi-ref: scripts/acceptance.mjs -->
<!-- bixi-make: architecture-check -->
<!-- bixi-make: runtime-config-check -->
<!-- bixi-make: backend-cloud-ci -->
<!-- bixi-make: backend-single-ci -->
<!-- bixi-make: frontend-ci -->
<!-- bixi-make: start-cloud -->
<!-- bixi-make: verify-cloud -->
<!-- bixi-make: start-single -->
<!-- bixi-make: verify-single -->

## 停止与交接

非 Bixi 项目、必需资料缺失或目标项目无法确定时，说明具体缺项；只询问会影响结果且无法推断的信息，不遍历整个用户目录。可继续的独立工作照常进行。交接包含目标、项目根目录、涉及文件、事实与假设、已执行命令和未完成项；复用仍有效的证据。
