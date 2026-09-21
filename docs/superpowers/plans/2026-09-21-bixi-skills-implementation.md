# Bixi Skills 技能套件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Bixi 仓库中创建可通过 `npx skills` 安装的五个 Agent 技能，覆盖上下文检索、全栈开发、缺陷修复、架构审查和双模验证。

**Architecture:** 技能源代码位于顶层 `skills/`，每个技能目录自包含一个 `SKILL.md`。技能通过仓库现有的 CodeGraph、架构文档、模块 README、示例实现和 Make 命令工作，不引入 Bixi 运行时依赖。`skills/evals/` 保存可重复的场景提示和预期检查点，`scripts/validate-skills.mjs` 负责包结构与引用的静态校验。

**Tech Stack:** Markdown、YAML frontmatter、Node.js 18+ 内置模块、现有 `codegraph` CLI、Bixi Make/Maven/Vite 验证命令、`npx skills` CLI。

**Status:** 2026-09-21 完整性复核修订；所有复选框是待开发任务。规格见[设计文档](../specs/2026-09-21-bixi-skills-design.md)。本轮只修订文档，不执行本计划。

---

## 文件结构和职责

**创建：**

- `skills/README.md`：安装、触发命令、技能职责和版本说明。
- `skills/bixi-context/SKILL.md`：CodeGraph 优先的项目上下文检索。
- `skills/bixi-feature/SKILL.md`：完整业务功能开发工作流。
- `skills/bixi-debug/SKILL.md`：最小回归测试驱动的缺陷修复工作流。
- `skills/bixi-architecture/SKILL.md`：模块边界和双模架构审查。
- `skills/bixi-verify/SKILL.md`：按改动范围执行质量门禁并报告证据。
- `skills/evals/context-search.md`：上下文检索场景。
- `skills/evals/README.md`：评测前置、基线方法、判定标准和记录格式。
- `skills/evals/feature-crud.md`：完整 CRUD 场景。
- `skills/evals/debug-regression.md`：缺陷定位和回归测试场景。
- `skills/evals/architecture-boundary.md`：依赖边界违规场景。
- `skills/evals/dual-mode-verification.md`：双模验证报告场景。
- `scripts/validate-skills.mjs`：技能目录、frontmatter、引用路径和命令文本静态检查。
- `scripts/validate-skills.test.mjs`：校验器正常包、坏包和保护说明不误报的测试。

评测记录保存在执行时打印的一次性目录；交付只摘录脱敏结论到 `skills/evals/README.md`。未来技能和脚本路径均相对 Bixi 根目录，不包含个人机器路径。

**不修改：** Bixi 后端、前端、SQL、`.docs/.chiwen.state.json` 和用户当前已有的无关工作区改动。

## 全局约束

- 技能名称固定为 `bixi-context`、`bixi-feature`、`bixi-debug`、`bixi-architecture`、`bixi-verify`。
- 每个 `SKILL.md` 必须以有效 frontmatter 开始，并提供 `name` 与 `description`。
- `description` 必须描述明确触发场景，避免五个技能在自然语言下互相抢占。
- 每个技能必须先检查工作区，再使用 CodeGraph；没有 CodeGraph 时明确说明并回退到 `rg`。
- 不复制完整架构文档，不引用个人路径，不打印密钥，不修改生成状态文件。
- 所有验证报告只能陈述实际执行结果。
- 默认顺序执行 Task 1–9；并行 Agent 仅在用户或适用约定明确要求时使用。
- 下文提交命令是获准提交时的检查点，不表示技能自动获得提交授权；未要求提交则保留文件和验证结果，不反复询问。
- 不创建能“通过校验”的空技能 stub。Task 2–6 分别写完整技能，Task 7 才执行全量包校验，避免前置任务依赖尚未完成的文件。
- 目标项目根目录、五个技能协作和异常处理遵循设计文档的共同契约；技能不得强制安装另外四个技能或 Superpowers 插件。

## 需求追踪与依赖

| 需求 | 实现任务 | 验证任务 |
| --- | --- | --- |
| R1 五个固定名称、可独立安装、双客户端 | Task 1–6 | Task 7、9 的 10 次安装与调用 |
| R2 Bixi 根目录、CodeGraph 优先与回退 | Task 2，其他技能复用契约 | Task 8 的 C1–C3 |
| R3 前后端/SQL/权限/审计/双模完整功能 | Task 3 | Task 8 的 F1–F3，F2 必须实际实现 |
| R4 回归失败到通过的修复流程 | Task 4 | Task 8 的 D1–D3 |
| R5 模块边界和合法适配器识别 | Task 5 | Task 8 的 A1–A3 |
| R6 按范围验证、环境准备和真实证据 | Task 6 | Task 8 的 V1–V3 |
| R7 无秘密/个人路径/无关工作区改动 | Task 1–6 | Task 7 结构检查、Task 8 行为检查 |
| R8 正反触发、基线、结果可复查 | Task 1 | Task 8、9 |

Task 1 → Task 2–6 → Task 7 → Task 8–9。任务 7 的静态通过不能代替任务 8、9；后两项因环境阻塞时保持未完成。

### Task 1: 创建包说明和评测约定

**Files:**
- Create: `skills/README.md`
- Create: `skills/evals/README.md`

- [ ] **Step 1: 创建目录并写入包 README**

创建 `skills/` 和 `skills/evals/` 目录，在 README 中固定以下安装示例：

```bash
npx --yes skills@1.7.0 add . --skill bixi-context --agent codex --copy -y
npx --yes skills@1.7.0 add . --skill bixi-feature --agent claude-code --copy -y
```

上述命令从 Bixi 根目录执行，表示项目级安装，不加 `--global`。README 列出五个技能、Codex 的 `$名称`、Claude Code 的 `/名称`、自然语言触发、CodeGraph 优先规则；指出这里尚无独立远程发布地址。

- [ ] **Step 2: 固定评测记录格式**

评测 README 使用以下字段，每次执行填写实际数据；未执行时状态写“未执行”，不能编造模型、退出码或时间。

```text
场景 ID / 客户端及模型版本 / Bixi 提交 / 技能内容摘要
前置条件 / 原始提示词 / 是否显式选择技能
未加载技能的基线结果 / 加载技能后的实际结果
改动文件 / 命令及退出码 / 脱敏证据位置
逐项断言结果 / 总体状态 / 剩余限制
```

- [ ] **Step 3: 固定基线执行规则**

在评测 README 中明确：Task 8 执行 15 个提示词前，使用同一模型、客户端、Bixi 提交和独立会话运行未加载技能的版本。这里不提前伪造基线结果；能力本已通过则如实记录，有改进空间时保存实际缺漏，之后再比较加载技能的效果。

- [ ] **Step 4: 校对说明文件并设置提交检查点**

```bash
test -s skills/README.md
test -s skills/evals/README.md
git diff --check
```

预期：两个非空说明文件且无空白错误。此时不宣称五个技能可安装。

```bash
git add skills/README.md skills/evals/README.md
git commit -m "docs: scaffold bixi skills package"
```

### Task 2: 实现 `bixi-context`

**Files:**
- Create: `skills/bixi-context/SKILL.md`
- Create: `skills/evals/context-search.md`

- [ ] **Step 1: 写入 frontmatter 和触发范围**

使用以下结构，保持命令名称和自然语言触发一致：

```yaml
---
name: "bixi-context"
description: "查询 Bixi 文档、实现位置和调用链；适用于解释现有项目机制，不单独承担功能实现或缺陷修复。"
---
```

- [ ] **Step 2: 写入检索顺序和输出格式**

先从用户工作项目定位 Bixi 根目录，不能把技能安装目录当成根。需要定位代码且存在索引时先 `codegraph explore`，无索引则跳过且不自动创建；失败时说明原因后回退 `rg`。按需读取架构、开发规范和目标模块资料，输出结论、证据路径/行号、调用关系和限制。文档与源码不一致必须区分。

- [ ] **Step 3: 写入评测场景**

在 `context-search.md` 写入 Task 8 的 C1–C3 全部输入和断言，含正常检索、无索引回退与非 Bixi 问题不误触发。

- [ ] **Step 4: 运行校验并提交**

运行：

```bash
test -s skills/bixi-context/SKILL.md
test -s skills/evals/context-search.md
```

预期：完整技能与评测文件存在；frontmatter、真实引用及行为验证分别由 Task 7、8 执行。

```bash
git add skills/bixi-context skills/evals/context-search.md
git commit -m "feat: add bixi context skill"
```

### Task 3: 实现 `bixi-feature`

**Files:**
- Create: `skills/bixi-feature/SKILL.md`
- Create: `skills/evals/feature-crud.md`

- [ ] **Step 1: 写入触发描述和前置分析流程**

采用与 Task 2 相同的两行双引号标量 frontmatter 格式，name 为 `bixi-feature`。description 限定 Bixi 新增/扩展功能，排除纯查询和仅审查。用户要求“只出方案”时只交付方案；要求实现时根据已有需求继续，无需重复审批普通实现步骤。

- [ ] **Step 2: 写入后端完成清单**

按固定顺序要求检查模块归属、Entity、DTO/VO、Mapper、Service、Controller、分页、详情、Jakarta Validation、`@HasPermission`、`@SysLog`、cloud/single 适配和测试。明确权限命名：`<domain>_<resource>_view|add|edit|del`。

- [ ] **Step 3: 写入 SQL、前端和验收清单**

要求同步检查 `01_init_all_tables.sql`、`03_add_indexes.sql`、`04_init_data.sql`，以及 Vue API、页面、表单校验、`v-auth`、加载/错误/空状态和响应式布局。共享行为变化修改 `scripts/acceptance.mjs`，实际断言 CRUD、非法输入、无权限拒绝、授权请求成功和操作日志持久化；两种模式复用同一用例。

- [ ] **Step 4: 写入生成器和边界规则**

说明可以使用 `bixi-generator`，但不能把生成器输出直接当作完成结果；必须审查权限、日志、SQL、双模和前端细节。禁止在 `bixi-single` 复制业务实现，禁止新增 localhost Feign 回环。

- [ ] **Step 5: 编写评测场景并提交**

`feature-crud.md` 写入 F1–F3：F1 只做方案；F2 在一次性项目副本真实完成示例工单；F3 纯解释不能触发开发。F2 的业务代码只留在副本，不能进入技能套件提交。

运行：

```bash
test -s skills/bixi-feature/SKILL.md
test -s skills/evals/feature-crud.md
git add skills/bixi-feature skills/evals/feature-crud.md
git commit -m "feat: add bixi feature skill"
```

### Task 4: 实现 `bixi-debug`

**Files:**
- Create: `skills/bixi-debug/SKILL.md`
- Create: `skills/evals/debug-regression.md`

- [ ] **Step 1: 写入触发和调试阶段**

固定流程为：复现 → CodeGraph 入口/调用方 → 影响范围 → 失败回归测试 → 最小修复 → 受影响测试 → cloud/single 验证。

frontmatter 的 name 为 `bixi-debug`，description 限定已有 Bixi 缺陷与失败测试；两个值均采用双引号标量。已有测试能够暴露问题则复用，缺少环境时报告未复现，不生成虚假修复结论。

- [ ] **Step 2: 写入专项检查**

要求检查 Feign/本地适配器、权限注解、操作日志、配置差异和双模行为；明确不能在没有失败证据时宣称修复，不能用部署模式特判绕过问题。

- [ ] **Step 3: 编写评测场景并提交**

`debug-regression.md` 写入 D1–D3：模式差异调查、可复现权限名错误的真实修复、新功能请求不误归入调试。D2 保留真实失败/通过日志；不能只输出“建议改权限名”即通过。

运行：

```bash
test -s skills/bixi-debug/SKILL.md
test -s skills/evals/debug-regression.md
git add skills/bixi-debug skills/evals/debug-regression.md
git commit -m "feat: add bixi debug skill"
```

### Task 5: 实现 `bixi-architecture`

**Files:**
- Create: `skills/bixi-architecture/SKILL.md`
- Create: `skills/evals/architecture-boundary.md`

- [ ] **Step 1: 写入依赖方向规则**

明确检查 `deployment -> biz -> api/common`，禁止 `common -> biz`、`api -> 对应 biz`、消费者直接依赖 Remote Feign 类型，以及 single localhost Feign 回环。

frontmatter 的 name 为 `bixi-architecture`，description 限定 Bixi 架构审查/迁移评估，排除只查位置；两个值均采用双引号标量。允许已有的 cloud/single 适配选择，不能把合法模式配置一概视为违约。

- [ ] **Step 2: 写入审查输出格式**

每个问题必须包含严重程度、文件/行号、违反规则、影响和最小修复建议；无问题时必须列出已检查范围。模块/API/Feign 变更后安排 `make architecture-check`。

- [ ] **Step 3: 编写评测场景并提交**

`architecture-boundary.md` 写入 A1–A3：三类违规、合法适配器不误报、单纯定位代码不触发完整审查；逐项断言问题和文件证据。

运行：

```bash
test -s skills/bixi-architecture/SKILL.md
test -s skills/evals/architecture-boundary.md
git add skills/bixi-architecture skills/evals/architecture-boundary.md
git commit -m "feat: add bixi architecture skill"
```

### Task 6: 实现 `bixi-verify`

**Files:**
- Create: `skills/bixi-verify/SKILL.md`
- Create: `skills/evals/dual-mode-verification.md`

- [ ] **Step 1: 写入按改动范围选择命令的规则**

明确以下映射：

frontmatter 的 name 为 `bixi-verify`，description 限定 Bixi 检查执行/交付验证；两个值均采用双引号标量。

```text
模块/API/依赖变化 -> make architecture-check、make runtime-config-check、对应 CI
后端行为变化     -> make backend-cloud-ci、make backend-single-ci
前端变化         -> make frontend-ci
共享行为变化     -> make verify-cloud、make verify-single
技能/文档变更    -> 套件静态、安装与场景检查（不机械运行业务 CI）
最终通用检查     -> 已有 .codegraph/ 时 codegraph sync .、git diff --check
```

- [ ] **Step 2: 写入证据报告格式**

按设计文档结果契约记录命令、目录、模式、退出码及证据。读 Makefile 判断 CI 已包含的门禁，避免重复执行；服务未启动先准备环境。cloud 与 single 串行，缺环境标为阻塞；启动日志中的生成密码必须脱敏后才能展示。

- [ ] **Step 3: 编写评测场景并提交**

`dual-mode-verification.md` 写入 V1–V3，涵盖完整开发结果、缺 Docker/混合失败和“只解释命令”。独立区分通过、失败、阻塞、未执行、不适用。

运行：

```bash
test -s skills/bixi-verify/SKILL.md
test -s skills/evals/dual-mode-verification.md
git add skills/bixi-verify skills/evals/dual-mode-verification.md
git commit -m "feat: add bixi verification skill"
```

### Task 7: 实现静态校验器和自动化校验测试

**Files:**
- Create: `scripts/validate-skills.mjs`
- Create: `scripts/validate-skills.test.mjs`

- [ ] **Step 1: 先写可执行的校验测试**

测试脚本用临时目录生成四套 fixture，并直接调用校验器导出的 `validate(root)`：

```text
valid-package       -> 0 个错误
missing-skill        -> 报缺失目录/SKILL.md
wrong-frontmatter    -> 报 name 不匹配、description 为空
invalid-reference    -> 报仓库文件或 Make 目标不存在
protected-guidance   -> 允许“不得修改 .docs/.chiwen.state.json”和“不要输出 .env”
secret-content       -> 报真实凭据形态和个人绝对路径
```

运行：

```bash
node --test scripts/validate-skills.test.mjs
```

预期：在校验器尚未实现时失败；测试不得把 Markdown 标题、具体措辞或技能正文快照当成行为契约。

- [ ] **Step 2: 实现校验器**

校验器使用 Node.js 内置 `fs`、`path`、`process`，导出可测试的 `validate(root)`，CLI 入口以非零状态退出。实现以下检查：固定五个目录和文件、受限 frontmatter 的 `name`/`description`、非空正文、名称唯一、技能 README/评测目录存在、已登记 Bixi 文件路径存在、已登记 Make 目标存在、敏感内容规则和保护说明豁免。敏感规则只拦截明确凭据形态（例如 `sk-...`、`ghp_...`、`AWS_SECRET_ACCESS_KEY=...`、`password=...` 的非占位值）和用户目录绝对路径，不拦截权限名、环境变量名或保护说明。输出聚合错误，不在发现第一项后终止。

- [ ] **Step 3: 运行校验器测试并修复到 GREEN**

运行：

```bash
node --test scripts/validate-skills.test.mjs
node scripts/validate-skills.mjs
```

预期：fixture 测试全部通过；当前工作区因五个 Skill/评测尚未创建而由 CLI 准确报告缺失，不能以跳过检查表示通过。

- [ ] **Step 4: 校验触发描述和引用边界**

校验器只检查结构与登记引用；触发是否冲突、技能是否做出正确判断交由 Task 8 的行为评测。README 不得声称脚本能证明客户端自动触发、代码实现质量或双模运行成功。

- [ ] **Step 5: 提交任务 7**

```bash
git add scripts/validate-skills.mjs scripts/validate-skills.test.mjs
git commit -m "test: validate bixi skills package"
```

### Task 8: 执行五组技能行为评测

**Files:**
- Modify: `skills/evals/README.md`
- Modify: `skills/evals/context-search.md`
- Modify: `skills/evals/feature-crud.md`
- Modify: `skills/evals/debug-regression.md`
- Modify: `skills/evals/architecture-boundary.md`
- Modify: `skills/evals/dual-mode-verification.md`

- [ ] **Step 1: 固定 15 个场景和断言**

每组三个场景：

```text
C1 正常检索 demo/task 完整链路；C2 无 .codegraph/ 回退且说明原因；C3 普通 Java 问题不触发 Bixi 事实检索
F1 用户明确“只出方案”；F2 在 disposable clone 实际新增一个 CRUD 并走完整验证；F3 仅查询已有 CRUD 不修改代码
D1 调查 single/cloud 权限差异；D2 修复可注入的权限名错误并记录失败到通过；D3 新增功能请求不误走 debug
A1 识别 api->biz；A2 识别 single Feign 回环；A3 合法 cloud Feign/single local 适配不误报
V1 后端+前端行为改动选择完整命令；V2 Docker 缺失或某一模式失败时准确阻塞；V3 只问命令解释不执行外部检查
```

每个文件记录输入、前置工作树、预期工具调用、必须出现的证据、禁止的副作用和通过/失败判定；F2/D2 的副本改动和日志不得复制回当前工作区。

- [ ] **Step 2: 执行无技能基线**

使用同一模型、客户端、Bixi 提交和提示词，在独立会话中运行不加载 Bixi 技能的版本并保存记录。能力本已通过则如实记录；有改进空间时保存实际缺漏，不能把模型已有能力写成技能贡献；不能运行的场景标“阻塞”并写原因。

- [ ] **Step 3: 执行加载技能的行为评测**

分别显式选择对应技能和不选择技能，确认每个技能可独立运行；复合场景记录主技能和交接信息。检查 CodeGraph、文件读取、命令、退出码、工作区差异及是否泄露配置。客户端自然语言自动触发只作附加结果，显式调用通过才算安装验收。

- [ ] **Step 4: 执行真实 F2 CRUD 副本验收**

创建一次性 git clone，在其中让 `bixi-feature` 完成一个最小业务（实体、Mapper、Service、Controller、SQL、菜单权限、前端和测试），再执行：

```bash
make architecture-check
make backend-cloud-ci
make backend-single-ci
make frontend-ci
make start-cloud && make verify-cloud
make start-single && make verify-single
```

两种模式必须使用同一 `scripts/acceptance.mjs` 用例；如果环境不足，保留已执行命令和阻塞原因，F2 不得判通过。

- [ ] **Step 5: 执行真实 D2 回归验收**

在另一份一次性副本中注入一个能被已有测试或最小 HTTP 用例暴露的权限名错误，先运行得到失败，再加载 `bixi-debug` 修复并重新运行同一用例得到通过；记录 diff、失败和通过输出。若无法安全注入或缺运行环境，标记阻塞而不是用文字推断。

- [ ] **Step 6: 汇总 15 个场景结果**

运行：

```bash
node --test scripts/validate-skills.test.mjs
node scripts/validate-skills.mjs
git diff --check
```

在评测 README 写入每个场景的状态、证据路径和限制。静态通过不覆盖行为场景；任何必需场景未执行则总体状态为未完成。

- [ ] **Step 7: 提交任务 8 的脱敏评测资料**

```bash
git add skills/evals
git commit -m "test: evaluate bixi skills behavior"
```

### Task 9: 独立安装、客户端调用和最终交付检查

**Files:**
- Modify: `skills/README.md`
- Modify: `skills/evals/README.md`
- Modify: `docs/superpowers/specs/2026-09-21-bixi-skills-design.md`
- Modify: `docs/superpowers/plans/2026-09-21-bixi-skills-implementation.md`

- [ ] **Step 1: 为每个技能执行 Codex 项目级安装**

从一次性临时项目目录分别执行五次，每个目录只安装一个技能：

```bash
repo_root="$(pwd)"
for skill in bixi-context bixi-feature bixi-debug bixi-architecture bixi-verify; do
  tmp_dir="$(mktemp -d)"
  (cd "$tmp_dir" && npx --yes skills@1.7.0 add "$repo_root" --skill "$skill" --agent codex --copy -y)
  test -f "$tmp_dir/.agents/skills/$skill/SKILL.md"
done
```

断言安装路径存在且内容只来自该技能；不得写入全局 `~/.codex`。临时目录路径和 CLI 输出需保存为脱敏证据。

- [ ] **Step 2: 为每个技能执行 Claude Code 项目级安装**

重复 Step 1，将 agent 参数替换为当前 CLI 支持的 Claude Code 标识，并记录 `npx skills add --help` 的实际选择。若环境没有 Claude Code，记录阻塞证据；不把 Codex 文件落盘成功冒充 Claude Code 调用成功。

- [ ] **Step 3: 验证显式调用和独立性**

在两个客户端（可用时）分别用 `$bixi-*`/`/bixi-*` 显式提示执行对应评测中的 C1、F1、D1、A1、V1，确认技能被加载、能定位当前 Bixi 根目录、无其他四个技能也能完成自身职责。自然语言自动触发单独记录为附加结果。

- [ ] **Step 4: 运行最终包与仓库门禁**

根据实际改动执行：

```bash
node --test scripts/validate-skills.test.mjs
node scripts/validate-skills.mjs
make architecture-check
codegraph sync .
git diff --check
```

如果改动只包含技能文档，不机械执行 Bixi 后端/前端 CI；如果评测副本改动涉及 Bixi 行为，CI 和运行验收在副本中单独报告。`codegraph sync .` 只在 `.codegraph/` 存在且本次改动影响索引时执行。

- [ ] **Step 5: 更新实际状态并完成检查点**

将 README、设计文档和计划中的“待完成”改为实际状态；只填写已经运行的命令和退出码。若双客户端、F2、D2 或运行级门禁阻塞，保留阻塞项，不能打上完整验收标记。

- [ ] **Step 6: 最终提交（仅在授权提交时）**

```bash
git add skills scripts/validate-skills.mjs \\
  docs/superpowers/specs/2026-09-21-bixi-skills-design.md \\
  docs/superpowers/plans/2026-09-21-bixi-skills-implementation.md
git commit -m "feat: complete bixi skills suite"
```

## 完成定义

- 五个技能目录和 `SKILL.md` 均存在且可安装。
- README、技能名称、触发命令、frontmatter、实际客户端参数完全一致。
- 评测 README 和五个场景文件覆盖 15 个正/反/边界场景；基线、技能结果和证据可复查。
- F2 真实 CRUD 与 D2 真实回归修复在一次性副本完成；若环境阻塞则明确未完成。
- 静态校验器测试覆盖正常包、缺文件、错误 frontmatter、失效引用、保护说明豁免和秘密检测。
- 五个技能分别完成 Codex 与 Claude Code 安装验收；客户端缺失时标记阻塞，不伪造通过。
- `make architecture-check`、`git diff --check` 以及适用的 `codegraph sync .` 通过。
- 行为改变的副本完成 cloud/single CI 和同一 `scripts/acceptance.mjs` 运行验收；不适用项目写明原因。
- 交付报告准确区分通过、失败、阻塞、未执行和不适用项目。
- 不修改 Bixi 运行时模块、`.docs/.chiwen.state.json` 或用户现有无关改动。
