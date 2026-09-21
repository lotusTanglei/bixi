# 技能评测

评测区分三种证据：文件/引用静态校验、五个技能在两个客户端中的独立安装与调用、实际任务行为。前两类通过不能证明第三类。

## 可重复流程

1. 固定 Bixi 源码提交、技能内容 SHA-256、客户端/模型版本和用户提示词；记录 `.codegraph/` 与依赖环境。
2. 同一场景使用独立干净会话，先运行无 Bixi 技能的基线，再加载技能；基线已有能力通过时如实记录。
3. 正向场景显式调用；反向触发场景只暴露可发现的技能并使用自然语言，不强制选择错误技能。不能只问模型“会不会触发”，需检查实际工具调用和文件差异。
4. F2、D2 在一次性 Bixi 副本实施，副本不带用户未提交改动和凭据。Docker 使用独立 Compose 项目及空闲端口，不改变其他部署。基线和技能版使用相同业务请求。
5. 每条断言单独判定；发现问题修改对应技能后重跑受影响场景。工具失败或缺依赖标记阻塞，不能以措辞正确替代真实行为。

## 场景目录

| 文件 | 场景 | 核心验证 |
| --- | --- | --- |
| [上下文](context-search.md) | C1–C3 | 真实引用、索引回退、通用 Java 不误触发 |
| [功能开发](feature-crud.md) | F1–F3 | 方案范围、完整工单实现、只读解释 |
| [缺陷修复](debug-regression.md) | D1–D3 | 证据/猜测、失败到通过、新需求不误修复 |
| [架构审查](architecture-boundary.md) | A1–A3 | 反向依赖、回环、合法适配不误报 |
| [交付验证](dual-mode-verification.md) | V1–V3 | 门禁覆盖、阻塞报告、只解释命令 |

## 记录格式

每条记录包括场景 ID、客户端及模型版本、Bixi 提交、技能摘要、前置条件、原始提示词、是否显式选择、基线结果、加载后结果、改动文件、命令/退出码、脱敏证据位置、断言结果及限制。

状态：通过、失败、阻塞、未执行、不适用。F2 实际 CRUD 双模验收、D2 真实回归测试、10 次独立安装和客户端调用都是完整验收必需项。自然语言正向命中另行统计；反向任务不产生越权修改属于必需断言。

静态保护说明可包含 `.env` 与 `.docs/.chiwen.state.json`；禁止的是泄露内容或修改受保护状态。原始日志留在权限受限的评测目录，仓库只保存脱敏结论和可验证摘要，不提交个人绝对路径、凭据、模型会话全文或临时业务实现。

## 执行记录

2026-09-21，源码基线 `c708381`。首版本地开发与验收已完成。加载技能后的 Codex 15 个场景已通过选用证据复核（包含修订后重测）；双客户端独立安装、正文读取与职责验证已完成。同客户端 F2/D2 无技能基线及最终对照审计均已完成；F2 基线存在功能缺陷和验收缺口，如实记为失败。

客户端和工具：Codex CLI 0.153.4，配置模型 `gpt-6-astra`、推理档 `xhigh`；Claude Code 2.1.220，实际返回模型 `deepseek-v4-flash[1m]`；Node.js 22.23.1、Java 17.0.2、Maven 3.9.9、Docker 29.4.0、skills CLI 1.7.0。Codex 记录的是相同配置与无模型覆盖的启动命令，不能冒充 API 返回模型元数据；Claude Code 是客户端名称，不能据此称其使用 Anthropic 模型。跨模型结果不用于证明能力提升。

原始独立 Agent 基线先于加载评测；其精确模型元数据不可得，因而另用相同 Codex CLI 配置、新会话和相同源码追加 F2/D2 基线。追加会话没有读取先前答案。13 个只读基线与对应加载场景使用同一 CLI 配置。基线本已通过的能力不虚构为技能贡献。

### 安装与独立调用

五个技能分别在两个客户端各安装一次；最终修订后再安装并校对全部内容。10 个目标目录各只有指定的一个 Bixi Skill，内容 SHA-256 与维护源一致，未写全局技能目录。

| 技能 | Codex 独立安装 / 实际读取与任务 | Claude Code 独立安装 / 实际读取与任务 |
| --- | --- | --- |
| bixi-context | 通过；C1、修订 C2 | 通过；读取版 C1 |
| bixi-feature | 通过；F1、真实 F2 | 通过；读取 v2 F1 |
| bixi-debug | 通过；修订 D1、真实 D2 | 通过；读取 v3 D1 |
| bixi-architecture | 通过；A1、A2 | 通过；读取 v3 A1 |
| bixi-verify | 通过；读取版 V1 | 通过；读取 v4 V1 |

Claude Code 的五个最终选用会话均满足本次调用与任务范围的验收；C1、D1、A1 仍保留文档读取或附带陈述的观察项，不能将此结论扩展为所有陈述、所有历史轮次均通过。

部分首次 slash / dollar 请求的事件只证明发现和请求完成，未直接记录正文注入；于是保留首次结果，增加“先读取当前项目 SKILL.md”再完成同一任务的独立会话，记录真实读取内容。这属于显式调用验证，不声称全部自然语言自动触发。V2 明确禁止命令，尊重该范围，正文注入在其事件中不可观测；`bixi-verify` 的加载能力由 V1 独立验证。

### 15 个场景对照

证据路径以下述 `EVIDENCE_ROOT` 为根；它是本次运行的权限受限本地目录别名，不是需要安装的依赖。每个 CLI 场景有 `prompt.txt`、`answer.txt`、`events.jsonl`、`meta.json`；真实场景另含命令日志、退出码和补丁。表中通过是相应断言经过输出、工具记录与必要源码复核，不能只由进程退出 0 推出。

| ID | 无技能基线 | 加载后选用结果 | 主要证据目录 / 说明 |
| --- | --- | --- | --- |
| C1 | 通过 | 通过 | `baseline/C1`、`with-skills-codex/C1`：后端/SQL/前端/权限/日志/测试链路 |
| C2 | 通过 | 通过（重测） | `baseline-noindex/C2`、`with-skills-v2-codex/C2`：真实无目录，实际解释四字段过滤及排序，未建索引 |
| C3 | 通过 | 通过 | `baseline/C3`、`with-skills-codex/C3`：只回答 Java 问题 |
| F1 | 通过 | 通过 | `baseline/F1`、`with-skills-codex/F1`：方案含完整业务链及双模验收，未实施 |
| F2 | 失败（构建和已有运行用例通过，完整断言未满足） | 通过 | `feature-cli-baseline-logs`、`feature-loaded-logs`：状态编辑缺陷、权限验收缺口及输出问题见下文 |
| F3 | 通过 | 通过 | `baseline/F3`、`with-skills-codex/F3`：只解释既有权限/审计 |
| D1 | 通过 | 通过（重测） | `baseline/D1`、`with-skills-v2-codex/D1`：未预认根因，修正首轮引用链接 |
| D2 | 通过 | 通过 | `debug-cli-baseline-logs`、`debug-loaded-logs`：原测试保留，真实红→绿，见下文 |
| D3 | 通过 | 通过 | `baseline/D3`、`with-skills-codex/D3`：新需求只出方案，未误修复 |
| A1 | 通过 | 通过 | `baseline/A1`、`with-skills-codex/A1`：反向依赖与复制业务违规 |
| A2 | 通过 | 通过 | `baseline/A2`、`with-skills-codex/A2`：single 回环违规 |
| A3 | 通过 | 通过 | `baseline/A3`、`with-skills-codex/A3`：合法条件适配未误报 |
| V1 | 通过 | 通过（补正文证据） | `baseline/V1`、`with-skills-read-codex/V1`：完整矩阵与前置条件，未执行构建/服务 |
| V2 | 通过 | 通过 | `baseline/V2`、`with-skills-codex/V2`：仅据给定混合日志判断不可交付，无伪造命令 |
| V3 | 通过 | 通过 | `baseline/V3`、`with-skills-codex/V3`：只解释构建与运行验收差异 |

13 个只读基线的审计检查了 121 次命令事件，未发现 Bixi 技能污染或越界写入。只读场景的无副作用判断基于捕获的工具记录及工作区证据，不等价于操作系统级全量审计。

### 真实开发与回归

F2 加载版保持 `ticketStatus` 与公共 `status` 分离，交付唯一后端、Vue 页面/API、SQL01/03/04、权限/审计、19 项聚焦测试，并让 `scripts/acceptance.mjs` 调用同一工单验收函数。两模式都创建真实受限用户与无业务菜单权限的角色，登录后确认权限缺失，再对分页、详情、新增、修改、删除断言 HTTP 403，并确认数据未被拒绝请求更改；管理员对应操作成功。新增、修改、删除日志以本次标记和既有日志 ID 边界核对持久化，不用注解反射替代权限执行。

| 实际检查 | 目录 / 模式 | 退出码 | 状态与证据 |
| --- | --- | --- | --- |
| 19 项工单聚焦测试 | F2 加载副本 / cloud | 0 | 通过；`016-focused-tests.log` |
| `make backend-cloud-ci` | F2 加载副本 / cloud | 0 | 通过；`025-cloud-ci.log` |
| `make backend-single-ci` | F2 加载副本 / single | 0 | 通过；`031-single-ci.log` |
| `make frontend-ci` | F2 加载副本 / 共享前端 | 0 | 通过；`026-frontend-ci.log` |
| `make start-cloud` / `make verify-cloud` | F2 加载副本 / cloud | 0 / 0 | 通过；`032-cloud-start.log`、`038-cloud-verify.log` |
| `make start-single` / `make verify-single` | F2 加载副本 / single | 0 / 0 | 通过；`039-single-start.log`、`041-single-verify.log` |
| `make stop` 与项目容器检查 | F2 加载副本 / 自有项目 | 0 | 自有容器已停止且确认不存在；`043-stop.log`、`044-final-protection-check.log` |

F2 同客户端基线已运行完毕：4 项聚焦测试、cloud/single 后端 CI、前端 CI、两模式启动和现有验收脚本均退出 0，自有项目容器已停止并确认不存在。但其脚本只覆盖管理员单条 CRUD、空标题拒绝和持久化日志，没有已登录无权限账户的 HTTP 403 断言，也没有多行分页和多 ID 删除验证，不能据命令成功判完整场景通过。

基线还存在状态编辑缺陷：详情同时返回 `ticketStatus` 和别名 `status`，Vue 表单合并后提交 `ticketStatus: CLOSED`、`status: OPEN`；后出现的旧值覆盖新值。独立探针按实际 Vue 键顺序构造请求，并使用现有编译类进行 Jackson 反序列化，确认最终仍为 `OPEN`；反序对照为 `CLOSED`。此证据是隔离的表单/反序列化复现，不是浏览器实测，也未修改被测实现。证据为 `f2-status-binding-probe.json`。

基线至少两次将本次副本生成的 `ADMIN_PASSWORD` 回显到模型的私有工具结果；随后改写本地日志进行脱敏，并造成一行 JSONL 损坏。审计只保存字段名、事件编号及布尔结果，不复制凭据，也不称其发生公开泄露。最终日志没有匹配值不能抹去先前回显；加载版未发现生成凭据回显匹配。证据为 `f2-credential-output-audit.json`，损坏行使基线事件记录存在完整性限制。

D2 加载版两 profile 都在原 3 项测试中取得 1 项失败，修复同一权限值后各 3 项通过；原测试 SHA-256 未变。可比 CLI 基线在 cloud 取得失败后修复，保留原测试并新增 7 项真实方法权限测试，两 profile 最终各 10 项通过，但没有 single 修复前失败记录。两者均只改一处生产权限值；D2 未执行真实 HTTP、审计入库或完整服务启动，未以聚焦测试宣称运行验收通过。

### 初次失败、修订与限制

- Codex C2 首轮仅列分页入口，没有回答过滤条件；补充按已有上下文与标准示例先给有范围答案后重测通过。D1 首轮存在错误文件链接，修正引用核对指引后重测。
- Claude Code 历史轮次出现 opaque token 解码建议、错误比较双模 `mode`、默认清卷、未复现即排除其他根因、无依据的 Bean 覆盖推断、聚焦测试参数遗漏，以及 CodeGraph/权威文档读取顺序遗漏。对应技能已做窄修订，最终选择 C1 读取版、F1 v2、D1/A1 v3、V1 v4；历史失败保留。不能描述成首轮全通过，也不能用这组有限场景保证任意模型每次都遵守全部指引。
- 克隆源码的前端 Dockerfile 引用未入库的 `bixi-ui/docker-build.sh`；真实 F2 副本统一应用同一 Dockerfile 环境修正并单独保留补丁。这是基线环境问题，未将补丁复制到产品工作区，不计为 Skill 收益。
- 普通克隆的 `.codegraph/` 只有占位配置，没有可用索引；探索/同步退出 1 均如实保留，无自行初始化。C2 另用完全无该目录的副本。目标项目已有索引，实际 `codegraph sync .` 已通过。
- 前端验证包含 ESLint 与生产构建；没有浏览器交互自动化，不声称 UI 全流程已测试。真实业务副本及原始启动凭据日志不进入本次交付。
- 每种条件只有一次真实开发执行；结论仅针对本次输出，不代表普遍或统计意义上的能力提升。耗时包含共享环境构建缓存、串行运行及等待，不作纯速度比较。
- 本地验收后，用户明确授权将技能、校验器与文档统一提交并推送到 GitHub。独立技能仓库发布和全局安装不属于本次交付范围。

### 包与目标仓库门禁

| 检查 | 实际命令 | 结果 |
| --- | --- | --- |
| 校验器回归 | `node --test scripts/validate-skills.test.mjs` | 退出 0，21/21 通过 |
| 包结构、引用与有限敏感规则 | `node scripts/validate-skills.mjs` | 退出 0 |
| 标准技能格式 | skill-creator 的 `quick_validate.py`，逐个技能 | 5/5 通过；开发环境临时 venv 安装 PyYAML，项目无新增依赖 |
| 架构与配置 | `make architecture-check`、`make runtime-config-check` | 目标项目两项退出 0 |
| 已有图谱 | `codegraph sync .` | 目标项目退出 0；未给无索引副本建索引 |
| 差异空白 | `git diff --check` | 目标项目退出 0 |

校验器首轮 18 项通过；独立审查发现本地 `file:` / Windows 路径绕过与括号链接误判后新增 3 项回归，修复后 21 项通过。它不是完整 YAML/Markdown 解析器或完整秘密扫描器，也不证明模型行为正确。

### 最终技能内容摘要

以下 SHA-256 对应最终维护源及最后一次独立安装。行为记录保留各次读取的正文；窄修订只重跑受影响场景，不覆盖或删掉旧结果。

| 技能 | SHA-256 |
| --- | --- |
| bixi-architecture | `b8751b012c1d50ff443ea30b1787a1427bc4321c2b7ea49e99439379d28294d9` |
| bixi-context | `501949ec4ff2f39a061fd0cfd8e6b0231df7e429497461f5f801319bd9e3f751` |
| bixi-debug | `0a8e3bbfb114321c4a1ab7527fdde14e5df3a82c9aba7853d93e6f49f3b86766` |
| bixi-feature | `7c9317d6f2dfbd8f57749335c17062eeace6f79e32f9d31d3a2918269d723b5a` |
| bixi-verify | `ce9ecf4afc7de06663ec8f694f6eb5981d2862d1ae407986fe302a3487f54885` |

独立审计索引：`baseline-audit.json`、`with-skills-codex-audit.json`、`claude-read-audit.json`、`d2-audit.json`、`f2-audit.json`。安装索引为 `installations.json`，相同配置证据为 `client-config.json`，目标仓库门禁日志位于 `package-gates/`。这些文件在本次 `EVIDENCE_ROOT` 中，仓库只保留本页脱敏结论。
