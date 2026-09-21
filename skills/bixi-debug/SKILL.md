---
name: "bixi-debug"
description: "当 Bixi 已有功能出现报错、接口异常、权限错误、测试失败、性能异常或 cloud/single 行为不一致，需要调查或修复时使用。新增业务方案和无故障的代码解释不以此技能为主。"
---

# Bixi 缺陷调查与修复

先取得复现证据，再做最小修复。用户只要求调查时保持只读；报告中的症状是线索，不能直接当成已证实根因。尚未复现时，不使用“只能由……导致”排除其他根因；最终给出源码事实、候选原因和验证顺序。

## 项目入口与约定

从用户当前目录或已指定项目运行 `git rev-parse --show-toplevel`，只检查 `AGENTS.md`、`pom.xml`、`bixi-module/`、`bixi-ui/` 是否存在，此时不读取 POM 或业务源码。下述路径相对此 Bixi 根目录解析，不能从 Skill 安装目录找业务代码。先检查工作区状态并保留已有改动。

随后检查 `.codegraph/`；存在时，在读取 POM/Makefile/业务源码或用 Glob/Grep/rg 定位代码之前，先用 `codegraph explore "问题或符号"`（或同等 MCP 工具）；失败则记录错误后回退 `rg` 和定向读取。不存在则说明并直接跳过，不自动创建索引。已经返回的当前源码不重复读取。

读取 `AGENTS.md`、`.docs/1_ARCHITECTURE.md`、`.docs/5_AI_DEVELOPMENT.md` 及相关模块 README；以当前源码核实文档，不固化某版本的部署模块列表。用户指令与适用仓库约定优先；默认中文，跟随用户语言。单独安装即可工作，无需其他 Skill 或插件，不递归调用、不自动派生 Agent。

不得修改 `.docs/.chiwen.state.json`，不输出 `.env` 或凭据，不交付秘密、构建产物、个人绝对路径。已有授权有效，普通可逆工作不重复确认；Skill 调用不额外授权提交、合并、发布或破坏性操作。

<!-- bixi-ref: AGENTS.md -->
<!-- bixi-ref: .docs/1_ARCHITECTURE.md -->
<!-- bixi-ref: .docs/5_AI_DEVELOPMENT.md -->

## 调试流程

1. 收集具体请求、模式、期望/实际结果及脱敏日志，记录首次失败的命令与退出码。经 CodeGraph 追踪入口、调用者和动态适配路径；逐项区分事实、假设与下一步验证。源码只能证明某个条件会导致错误，不能证明历史请求当时满足该条件；没有请求或复现证据时，“已证实”只列当前源码事实，不能断言当时的令牌、权限集合或配置确实异常。
2. 查看同一业务是否由 cloud/single 共享；检查实际路由、鉴权、角色菜单数据、权限名及日志链。先核实令牌格式；reference/opaque token 不能按 JWT 解码，应核对脱敏授权请求或服务端授权元数据。跨模块沿 `*-api/service` 追踪，cloud 是 Feign，single 是本地适配；不把“只在单体出现”的报告当成业务代码只影响单体的证据。
3. 优先复用能暴露问题的已有测试，保留有效断言；缺少覆盖才补最小回归测试。先运行得到与报告一致的失败，再改代码，最后运行同一测试得到通过。测试未复现时继续缩小条件，不为形式新建重复测试，也不删除断言来变绿。
4. 修复真实原因，维持 `deployment → biz → api/common`，公共/API 不依赖 biz，single 不复制业务。合法的 Feign/本地条件装配可以保留；不得用模式分支绕过缺陷、关闭权限或增加 localhost Feign 回环。
5. 权限名应与后端、`v-auth` 和菜单 SQL 一致：`<domain>_<resource>_view|add|edit|del`；所有写操作保留 `@SysLog`。检查修复是否同时影响两种模式、权限拒绝与审计持久化。

## 验证与报告

运行受影响模块测试；架构/依赖/适配变化运行 `make architecture-check` 和 `make runtime-config-check`。后端行为变更运行 `make backend-cloud-ci`、`make backend-single-ci`；前端变更加 `make frontend-ci`。共享行为使用 `scripts/acceptance.mjs`，在自己的环境顺序 `make start-cloud` / `make verify-cloud`，再 `make start-single` / `make verify-single`。测试反射到注解不等于 HTTP 权限已执行。

先核对 Java 17、Maven、Node、Docker/Compose、配置与端口，不停止其他任务服务，不并发切换模式。复现优先使用新隔离环境或增量迁移；开发环境也不默认可清卷，不把 `make reset` 列作复现前置。启动日志可能含凭据，保存在权限受限本地文件，只输出脱敏证据。存在 `.codegraph/` 时 `codegraph sync .`；最后 `git diff --check`。

输出根因及文件/行号、修复范围、失败→通过证据、两种模式影响、实际命令/工作目录/模式/退出码及未执行项。输出前核对引用文件存在，不把省略过的路径拼成文件链接。没有复现/回归证据，或只有提供的日志摘要时，不宣称已修复。无法复现则止于调查结论、候选原因及下一步；缺环境列阻塞，不能推断运行验收通过。

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
