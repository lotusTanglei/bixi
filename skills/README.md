# Bixi Skills

为 Bixi 项目开发提供上下文、功能实现、调试、架构审查和交付验证的五个独立技能。使用项目当前的 `AGENTS.md`、源码和开发规范，不固定旧版本的部署模块列表。

| 技能 | 适用任务 | 不承担 |
| --- | --- | --- |
| `bixi-context` | 查文档、代码位置、调用链、配置与示例 | 自动改代码 |
| `bixi-feature` | 新增/扩展 Bixi 业务、接口、页面和 CRUD | 将“只出方案”变成实施 |
| `bixi-debug` | 复现、定位和修复已有缺陷 | 无证据猜测根因 |
| `bixi-architecture` | 模块/依赖/跨模块契约和双模审查 | 未授权的架构重构 |
| `bixi-verify` | 执行门禁、评价交付证据 | 未运行即宣称通过 |

## 安装

从本仓库根目录执行项目级安装（Node.js 18+、npm；首次运行需网络）：

```bash
npx --yes skills@1.7.0 add . --skill bixi-context --agent codex --copy -y
npx --yes skills@1.7.0 add . --skill bixi-feature --agent claude-code --copy -y
```

把 `--skill` 的值换成表中任一名称即可单独安装。安装全部五个时显式列出它们：

```bash
npx --yes skills@1.7.0 add . --skill bixi-context bixi-feature bixi-debug bixi-architecture bixi-verify --agent codex --copy -y
```

维护源位于 `skills/`，安装副本由 CLI 放到客户端发现目录；修改源后重新安装复制版本。不使用 `--global`，不会更新用户全局技能。这里尚未发布独立远程技能仓库。

## 使用

Codex 输入 `$bixi-context 定位 demo/task 的权限校验`；Claude Code 输入 `/bixi-context 定位 demo/task 的权限校验`。其他四个名称相同。也可以自然语言描述任务，例如“在 Bixi 新增工单管理”或“审查 Bixi 的模块依赖”。自然语言选择取决于客户端和模型，不能保证每次触发；可显式选择。

技能在用户的目标 Bixi 项目中解析路径，不以自身安装目录为项目根。单个技能不依赖另外四个、Superpowers、远程搜索服务或本包维护脚本。只询问无法由现有上下文确定的关键信息，不重复请求已经获得的授权。

存在 `.codegraph/` 时先调用 CodeGraph；缺失时跳过且不建立索引，工具失败时说明后回退 `rg`。所有工作遵循目标仓库约定，保留用户已有改动，不输出凭据、不修改 `.docs/.chiwen.state.json`。技能调用本身不授权提交、合并或发布。

## 维护与验证

从仓库根目录执行（Node.js 18+ 内置模块，无 npm 依赖）：

```bash
node --test scripts/validate-skills.test.mjs
node scripts/validate-skills.mjs
make architecture-check
make runtime-config-check
git diff --check
```

校验器检查结构、登记引用和有限敏感内容模式，不证明自动触发或业务正确性。[评测说明](evals/README.md)列出场景、独立安装及实际证据。技能包本身不增加 Bixi 后端、前端菜单、数据库表或运行时依赖；真实开发评测只在隔离副本运行。

## 当前交付状态

首版本地开发与验收已完成：5 个 Skill、21 项校验器测试、10 次独立安装、双客户端正文读取与调用、15 个 Codex 场景（含重测）已取得验证证据。完整 CRUD 在隔离副本通过双模构建、运行权限拒绝与审计验收；真实缺陷修复取得回归测试失败到通过的证据。基线对照、历史失败和验证限制见[评测记录](evals/README.md)。
