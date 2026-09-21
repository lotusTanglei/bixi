---
name: "bixi-context"
description: "当用户需要查询 Bixi 的代码位置、调用链、架构、权限、SQL、配置或既有实现机制时使用。适用于只读项目调查；普通 Java 知识问答不需要此技能。"
---

# Bixi 上下文检索

根据当前源码回答项目事实，保持只读。提到 CRUD 不等于要求开发；仅查询或解释时不修改文件、不启动服务、不运行有写入副作用的索引同步。

## 项目入口与约定

从用户当前目录或已指定项目运行 `git rev-parse --show-toplevel`，只检查 `AGENTS.md`、`pom.xml`、`bixi-module/`、`bixi-ui/` 是否存在，此时不读取 POM 或业务源码。下述路径相对此 Bixi 根目录解析，不能从 Skill 安装目录找业务代码。先检查工作区状态并保留已有改动。

随后检查 `.codegraph/`；存在时，在读取 POM/Makefile/业务源码或用 Glob/Grep/rg 定位代码之前，先用 `codegraph explore "问题或符号"`（或同等 MCP 工具）；失败则记录错误后回退 `rg` 和定向读取。不存在则说明并直接跳过，不自动创建索引。已经返回的当前源码不重复读取。

读取 `AGENTS.md`、`.docs/1_ARCHITECTURE.md`、`.docs/5_AI_DEVELOPMENT.md` 及相关模块 README；以当前源码核实文档，不固化某版本的部署模块列表。用户指令与适用仓库约定优先；默认中文，跟随用户语言。单独安装即可工作，无需其他 Skill 或插件，不递归调用、不自动派生 Agent。

不得修改 `.docs/.chiwen.state.json`，不输出 `.env` 或凭据，不交付秘密、构建产物、个人绝对路径。已有授权有效，普通可逆工作不重复确认；Skill 调用不额外授权提交、合并、发布或破坏性操作。

<!-- bixi-ref: AGENTS.md -->
<!-- bixi-ref: .docs/1_ARCHITECTURE.md -->
<!-- bixi-ref: .docs/5_AI_DEVELOPMENT.md -->

## 检索与输出

1. 依据问题选择入口，经 CodeGraph、权威文档和模块 README 后补充定向源码。对完整业务链追踪 `前端页面 → API → 路由 → Controller → Service → Mapper → SQL`，同时核对测试、权限与日志；允许继承实现，不虚构不存在的 Mapper XML。
2. 示例后端在 `bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo`；前端为 `bixi-ui/src/api/demo/task.ts` 与 `bixi-ui/src/views/demo/task`。SQL 在 `bixi-project-documents/sql/01_init_all_tables.sql`、`03_add_indexes.sql`、`04_init_data.sql`；共享黑盒验收是 `scripts/acceptance.mjs`。
3. 跨模块沿 `*-api/service` 传输无关契约追踪实际装配；cloud 的 Feign 与 single 的本地实现复用业务逻辑。文档与当前源码有差异时分别说明。
4. 先给结论，再给核实过的文件与行号、调用关系和相关命令，最后说明未找到项及证据边界。源码、注解或测试代码的存在不代表测试已经运行，更不代表权限拒绝、审计落库已得到验证。

问题没有指定唯一接口时，先利用当前上下文与标准示例给出有明确范围的实际答案；例如分页过滤要追到 Query、Service 或 Mapper 的条件构造，不能只列 Controller 入口就结束。确实存在多个不同实现则说明差异，再补问无法确定的部分。

普通语言知识问题直接解答，不为使用本技能而读取项目。相关命令只作为后续建议，未经执行不填写通过或退出码。

<!-- bixi-ref: bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo -->
<!-- bixi-ref: bixi-ui/src/api/demo/task.ts -->
<!-- bixi-ref: bixi-ui/src/views/demo/task -->
<!-- bixi-ref: bixi-project-documents/sql/01_init_all_tables.sql -->
<!-- bixi-ref: bixi-project-documents/sql/03_add_indexes.sql -->
<!-- bixi-ref: bixi-project-documents/sql/04_init_data.sql -->
<!-- bixi-ref: scripts/acceptance.mjs -->

## 停止与交接

非 Bixi 项目、必需资料缺失或目标项目无法确定时，说明具体缺项；只询问会影响结果且无法推断的信息，不遍历整个用户目录。可继续的独立工作照常进行。交接包含目标、项目根目录、涉及文件、事实与假设、已执行命令和未完成项；复用仍有效的证据。
