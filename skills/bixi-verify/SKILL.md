---
name: "bixi-verify"
description: "当用户要求验证 Bixi 改动、执行质量门禁、检查测试覆盖、评估交付证据，或判断是否可提交、合并和交付时使用。只问命令含义时仅解释，不自动执行检查。"
---

# Bixi 交付验证

按改动选择门禁，报告实际证据。只要必需检查失败、阻塞或未执行，就不能给出总体通过；“计划执行”和“已通过”是不同状态。

## 项目入口与约定

从用户当前目录或已指定项目运行 `git rev-parse --show-toplevel`，只检查 `AGENTS.md`、`pom.xml`、`bixi-module/`、`bixi-ui/` 是否存在，此时不读取 POM 或业务源码。下述路径相对此 Bixi 根目录解析，不能从 Skill 安装目录找业务代码。先检查工作区状态并保留已有改动。

随后检查 `.codegraph/`；存在时，在读取 POM/Makefile/业务源码或用 Glob/Grep/rg 定位代码之前，先用 `codegraph explore "问题或符号"`（或同等 MCP 工具）；失败则记录错误后回退 `rg` 和定向读取。不存在则说明并直接跳过，不自动创建索引。已经返回的当前源码不重复读取。

读取 `AGENTS.md`、`.docs/1_ARCHITECTURE.md`、`.docs/5_AI_DEVELOPMENT.md` 及相关模块 README；以当前源码核实文档，不固化某版本的部署模块列表。用户指令与适用仓库约定优先；默认中文，跟随用户语言。单独安装即可工作，无需其他 Skill 或插件，不递归调用、不自动派生 Agent。

不得修改 `.docs/.chiwen.state.json`，不输出 `.env` 或凭据，不交付秘密、构建产物、个人绝对路径。已有授权有效，普通可逆工作不重复确认；Skill 调用不额外授权提交、合并、发布或破坏性操作。

<!-- bixi-ref: AGENTS.md -->
<!-- bixi-ref: .docs/1_ARCHITECTURE.md -->
<!-- bixi-ref: .docs/5_AI_DEVELOPMENT.md -->

## 先取证，再列矩阵

用户要求“只给验证计划”时，下面仍是允许的只读取证，不是构建或运行验收：

1. 用 `git rev-parse --show-toplevel` 和 `git status --short` 确认根目录与工作区状态；未执行 status 就不描述文件是否未跟踪。
2. 只检查 `.codegraph/` 是否存在。存在则先调用 `codegraph explore "Makefile scripts/acceptance.mjs 验证命令与验收入口"`，记录实际成功或失败，再继续；不能先读 Makefile 或搜索验收代码。
3. 读取 `AGENTS.md`、`.docs/1_ARCHITECTURE.md`、`.docs/5_AI_DEVELOPMENT.md`，然后才按需要读取 Makefile 和验收源码，形成下面的矩阵。

如果用户明确禁止所有命令或只要求评价给定日志，则直接依据给定材料回答，标明来源，不声称完成上述检查。

## 选择检查

| 改动范围 | 所需验证 |
| --- | --- |
| 模块、API、依赖、Feign/本地适配 | `make architecture-check`、`make runtime-config-check` 及双模后端 CI |
| 后端业务行为 | `make backend-cloud-ci`、`make backend-single-ci`，加聚焦回归测试 |
| Vue、前端 API/页面 | `make frontend-ci` |
| 两种模式共享行为 | 两个模式运行同一 `scripts/acceptance.mjs`；真实 CRUD、非法请求、权限拒绝/成功及写操作审计落库 |
| 仅 Skill/文档 | 适用的静态校验、安装、客户端与场景评测；不据此宣称业务运行验收通过 |
| 最终检查 | 存在 `.codegraph/` 时 `codegraph sync .`，以及 `git diff --check` |

`backend-*-ci` 是构建与测试；`verify-*` 是依赖正在运行服务的 HTTP 验收。前者通过不能替代后者。套件维护仓库存在校验器时可用 `node --test scripts/validate-skills.test.mjs` 和 `node scripts/validate-skills.mjs`；单独安装不要求目标项目拥有这些维护文件。

补充命令也要核对当前构建配置。Maven 用 `-am -Dtest=指定测试` 时，无该测试的依赖模块可能先失败；按当前 Surefire 配置加 `-Dsurefire.failIfNoSpecifiedTests=false`，并核对目标模块确实执行了指定测试，不能把“没有执行测试”算通过。

## 运行前置和顺序

检查 Java 17、Maven、Node、Docker/Compose、本地配置及可用端口；环境以当前 Makefile 和启动脚本为准。不要输出配置秘密。保留既有 `.env`，需要时用 `make init-env` 创建自己的本地配置并运行 `make doctor`。

先核对正在运行的服务与 Compose 项目。使用本任务的隔离环境，不停止其他任务部署、不重置持久数据。同一环境按 `make start-cloud` → `make verify-cloud` → `make start-single` → `make verify-single` 顺序；不要并发构建或切换两种模式。当前 `scripts/bixi.sh` 会打印初始凭据，启动原始输出重定向至权限受限本地日志，只展示脱敏摘要。环境不足时继续可运行的独立检查，并标记对应项阻塞。

## 结果契约

使用表格：`检查项 | 适用原因 | 实际命令 | 工作目录 | 模式 | 退出码 | 状态 | 脱敏证据位置`。

状态限于**通过、失败、未执行、阻塞、不适用**。没有执行的命令退出码写“—”；用户提供的摘要标注来源，不冒充亲自执行，也不编造日志位置。用户只要求验证计划或解释时，给计划/解释即止，不运行服务或构建。

末尾给出是否满足交付标准、失败原因和剩余必需项。测试日志全文可能含敏感内容，引用脱敏片段。发现失败先报告证据，在当前授权内做必要修复；不扩展为无关重构。静态关键词命中不证明 Skill 触发正确，安装文件存在不证明客户端实际调用成功。

<!-- bixi-ref: scripts/acceptance.mjs -->
<!-- bixi-ref: scripts/bixi.sh -->
<!-- bixi-make: architecture-check -->
<!-- bixi-make: runtime-config-check -->
<!-- bixi-make: backend-cloud-ci -->
<!-- bixi-make: backend-single-ci -->
<!-- bixi-make: frontend-ci -->
<!-- bixi-make: init-env -->
<!-- bixi-make: doctor -->
<!-- bixi-make: start-cloud -->
<!-- bixi-make: verify-cloud -->
<!-- bixi-make: start-single -->
<!-- bixi-make: verify-single -->

## 停止与交接

非 Bixi 项目、必需资料缺失或目标项目无法确定时，说明具体缺项；只询问会影响结果且无法推断的信息，不遍历整个用户目录。可继续的独立工作照常进行。交接包含目标、项目根目录、涉及文件、事实与假设、已执行命令和未完成项；复用仍有效的证据。
