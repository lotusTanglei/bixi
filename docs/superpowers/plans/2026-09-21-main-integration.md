# Main 最新代码整合 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将当前已实现的工作流、安全增强、共享前端、SQL、运行脚本和 Skills 整合为可验证的 main，并同步 GitHub。

**Architecture:** 在已有隔离工作树中从 main 创建整合分支，导入原工作区的稳定文件快照，保留唯一 cloud/single 业务实现。原工作区仍由其他开发任务使用，整合过程中不修改它的代码、暂存区、凭据或用户维护状态。

**Tech Stack:** Java 17、Maven、Vue 3、Node.js、MySQL、Docker Compose、现有 Make 门禁与双模验收脚本。

本次整合当前实现，不扩展尚未实现的工作流后续阶段、租户或安全路线图。用户本次已授权提交、合并和推送；各历史开发记录中的“不提交”是原开发任务约束，不阻止本次独立整合。`.docs/.chiwen.state.json`、本地凭据、构建产物与索引不进入提交。

## Task 1: 固定整合范围

- [x] 核对本地/远程分支及已有 main，确认 Skills 已包含。
- [x] 在已有隔离工作树建立 `codex/main-integration-20260921`。
- [x] 按原工作区相对 HEAD 的修改、新增和删除生成稳定快照，保留内容摘要；排除用户维护状态和忽略文件。
- [x] 审查工作流/可靠消息、安全/权限、SQL/部署和前端改动，确认当前实现与进度记录一致；扫描意外凭据和个人路径，只记录位置、不输出敏感值。

## Task 2: 构建与回归

- [x] 使用 Java 17 执行 `make backend-cloud-ci`，保留退出码与完整私有日志。
- [x] 在 cloud 完成后执行 `make backend-single-ci`，避免两 profile 同时 clean 同一构建目录。
- [x] 执行 `make frontend-ci`、`node --test scripts/test-workflow-ui.mjs`、认证代理与 SQL 迁移的既有回归。
- [x] 执行 `node --test scripts/validate-skills.test.mjs` 和 `node scripts/validate-skills.mjs`。
- [x] 若失败，先定位与复现；复用有效既有测试，必要时增加有行为意义的失败回归，再最小修复并复测。新增领域只使用当前模块，不复制双模业务。

## Task 3: 数据库与真实双模验收

- [x] 确认本地 Docker 项目和占用端口，使用本次独占项目、独占端口与全新本地随机凭据。
- [x] 核实并运行 `make workflow-mysql-test`、`make reliable-mysql-test`，按脚本支持的环境变量隔离数据库；不能借用其他任务的数据。
- [x] 按序构建/启动 cloud，执行 `make verify-cloud` 与 `make verify-security-cloud`；核对表单认证补充用例和工作流开关覆盖。
- [x] 切换 single，执行相同业务断言及 `make verify-single`、`make verify-security-single`。按既有用例补充工作流开启/关闭的组合。
- [x] 只停止本次自有 Compose 项目，确认无遗留自有容器。启动日志保存在权限受限的本地目录，不向工具结果输出初始凭据。

## Task 4: 独立审查与增量同步

- [x] 分别审查模块边界/契约及代码质量，修复实际阻断问题并重跑受影响检查。
- [x] 再次比对原工作区与首次快照；导入期间新增的已实现代码。整合副本已有修复时执行三方比较，不覆盖任一方修改。
- [x] 对新增差异补必要测试。提交前记录本次收录快照时间、文件摘要及未实现功能边界，不将持续开发中的后续计划声称完成。

## Task 5: 提交并同步 main

- [x] 更新本次验收结果，确认 `git diff --check`、包校验、敏感内容检查和必要门禁通过。
- [x] 对适用的已有 CodeGraph 索引执行同步；无可用索引的隔离副本不初始化。
- 只提交审查过的整合文件；确认提交中不含 `.env`、用户维护状态、构建产物或个人绝对路径。
- 获取远程 main 最新状态，在隔离工作树合并；有并发提交则先整合与复测，再进行非强制推送。
- 读取 GitHub main 验证提交哈希，核对全部已有分支及本次已验收范围已覆盖；报告真实结果和剩余开发范围。

提交前验证已完成，详见 `.docs/7_MAIN_INTEGRATION.md`。最后三项为发布步骤，实际结果以 Git 提交记录、GitHub main 哈希及本次交付报告为准。
