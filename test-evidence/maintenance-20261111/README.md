# Bixi 收敛维护执行记录（2026-11-11，Asia/Shanghai）

**有证据**：本文件记录首次 `.idea` 修复；后续在 `a1ed48c` 发现并修复 Nginx 配置个人路径漏检，当前最终维护提交与完整新证据见[运行配置维护补充](runtime-path-followup/README.md)。本文件下面的基线和命令归属保持原样，不替代后续提交证据。

## 基线与修改

- **有证据**：开工 `git status --short --branch` 为 `## main...origin/main [ahead 1]`；HEAD 为 `c740b8335b85ad99603df6b4d3f85968742b6b1e`，`git log --oneline origin/main..main` 只有该提交，工作区干净。见 `command-output-excerpts.txt`。
- **有证据**：本轮仅修改 `scripts/verify-runtime-config.sh` 的运行时个人路径扫描，使其排除被忽略的 `.idea` 元数据。原门禁因 `.idea/workspace.xml:101` 的 IDE 本地路径失败，见 `initial-failure.txt`。真实运行配置文件的个人路径检查、浏览器源码与构建产物密钥检查均保留。
- **有证据**：`README.md`、`.docs/PROJECT_STATUS.md`、`.docs/RELEASE_1_0_0_LEDGER.md`、`.docs/3_ROADMAP.md` 与历史 `test-evidence/verify-20261111/README.md` 对齐维护范围、发行暂停、历史证据归属和未来建议。最终维护提交可用 `git log -1 --format=%H -- scripts/verify-runtime-config.sh` 定位；OPEN 总索引另记录最终 hash。
- **有证据**：先执行 CodeGraph 再文本检索；认证、租户、权限、审计/导入、Workflow、AI 与配置入口及覆盖测试见 `codegraph-entry-map.txt`。本轮没有改这些业务实现，也不把定位结果视为完整安全审计结论。

## 当前验证

以下均在 `c740b83` 加本轮单行脚本修复的工作树执行，未使用测试跳过参数。`command-output-excerpts.txt` 保存本次工具输出的安全摘录；`runtime-config-final.txt` 是最终工作树直接重定向保存的完整静态门禁输出。

| 命令 | 退出码 | 通过/失败/跳过与证据 |
| --- | ---: | --- |
| `make runtime-config-check`（修复前） | 2 | 失败：误扫忽略的 IDE 元数据，`initial-failure.txt` |
| `make architecture-check` | 0 | 双模 Maven reactor 依赖图检查通过，`command-output-excerpts.txt` |
| `make runtime-config-check`（修复后与最终文档工作树） | 0 | 既有配置/安全/脚本契约通过，`runtime-config-final.txt` |
| `make backend-cloud-ci` | 0 | 28/28 reactor 模块 SUCCESS；无失败/错误；环境条件跳过见下文 |
| `make backend-single-ci` | 0 | 25/25 reactor 模块 SUCCESS；最近一次 single/shared Surefire 共 1197 tests、0 failures、0 errors、79 skipped，`surefire-summary.json` |
| `make frontend-ci` | 0 | 既有 Node 合约测试、eslint、生产构建通过；现有 `vform3-builds` eval 警告保留，`command-output-excerpts.txt` |
| `rg 'VITE_OAUTH2_|VITE_PWD_ENC_KEY|basicAuth' bixi-ui/dist` | 1 | 预期无匹配，前端产物无上述密钥变量/认证标识；不是命令失败豁免 |
| `make runtime-config-check`（临时 `deploy/maintenance-personal-path-probe.yml` 含个人路径） | 2 | 预期拒绝，证明运行资源检查仍生效；`runtime-resource-negative.txt`，探针已删除 |
| `codegraph sync .` | 0 | `Synced 95 changed files`、`Done`；索引保持忽略，不提交生成物 |
| `git diff --check` | 0 | 无空白错误 |

**有证据**：Surefire 的 79 项跳过由既有测试环境条件触发：78 项因未设置 `OUTBOX_TEST_JDBC_URL`，另 1 项 Quartz JDBC 双节点故障接管因未设置 `BIXI_QUARTZ_HA_JDBC_URL`。逐套件跳过信息及报告路径保存在 `surefire-summary.json`；没有设置 skip 参数，也没有把跳过项写成通过。共享模块报告来自最后完整 single 门禁，cloud 专属 gateway/monitor 报告保留自紧邻的 cloud 门禁，快照归属已逐项标注。

## 范围与未完成项

- **文档声明（用户已确认）**：发行暂不处理；本轮不启动四环境发行验收、不创建 tag、不新增发行 `PASS`，没有推送 GitHub。
- **有证据**：本轮没有产品运行时行为或共享业务契约变更，因此未重跑 `make verify-cloud` / `make verify-single` 应用运行验收；`verify-20261111/README.md` 已明确为 `2b5649d` 的历史文档声明，不替代当前提交证据。
- **文档声明**：外部供应商、完整浏览器/跨租户矩阵、目标环境恢复和高可用验证继续保持原有未验收边界；本轮不据此宣称消除所有既有安全缺口。
- **有证据**：没有处理 ActBox、WebDeck、OpsPilot，没有新增跨项目共享运行时代码；三项目只统一证据字段。
- **推断**：本次失败来自扫描范围包含忽略的 IDE 文件，而非运行资源含本地路径；依据修复前失败与修复后正/负向门禁结果。除此之外不作新的产品能力推断。

## OPEN 索引更新建议与未来方向

**文档声明**：OPEN 将 Bixi 行更新为本轮最终维护提交及本目录证据，保留“发行暂不处理”，不要把历史全新卷本机验证写成 Ubuntu 四环境发行通过。

**文档声明：建议，待确认，当前不实施**。“SPI → Webhook/SDK → 模板机制”仅作为未来收敛方向；签名模板/插件目录仅记录方案，不建立目录、不引入运行时加载。不新增业务模块、工程构建器、移动端、支付或 Kubernetes。
