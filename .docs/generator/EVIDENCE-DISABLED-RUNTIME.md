# Phase 2 Disabled Runtime Matrix

更新：2026-09-26。本文记录本轮在隔离 Compose 项目中执行的 disabled 运行态复验。凭据只存在于临时 env 文件，未写入本文或 JSON 摘要。

## Cloud disabled

隔离项目：`bixi-phase2-cloud-disabled-audit`。本轮使用端口 `28182`（frontend）、`29993`（Gateway）、`23318`（MySQL）、`26391`（Redis）、`25684`（RabbitMQ）和 `28860`（Nacos）。

核心验收命令：

```text
BIXI_MODE=cloud \
  BIXI_ENV_FILE=/tmp/bixi-phase2-cloud-disabled-audit.env \
  node scripts/acceptance.mjs
```

进程退出码为 `0`。真实管理员登录、角色/权限、示例任务 create/page/details/update/invalid-request/delete、参数校验和三条操作审计均通过；`WORKFLOW_ENABLED=false` 时 Workflow 菜单隐藏，Workflow 端点返回 HTTP 404。原始摘要为 `target/phase2-runtime/cloud-disabled-audit-acceptance.json`。

同一栈的 Generator 黑盒命令退出码为 `0`：

```text
BIXI_MODE=cloud \
  BIXI_ENV_FILE=/tmp/bixi-phase2-cloud-disabled-audit.env \
  node scripts/generator-acceptance.mjs \
    --mode cloud \
    --env-file /tmp/bixi-phase2-cloud-disabled-audit.env
```

单表 `sys_public_param` 产生 17 个预览/ZIP 条目和 9 个 XLSX 条目；父子表 `sys_dict/sys_dict_item` 产生 21 个预览/ZIP 条目和 9 个 XLSX 条目。服务端生成、同步、过期模板版本拒绝、权限、匿名拒绝和操作审计均通过。原始摘要为 `target/phase2-runtime/cloud-disabled-audit-generator.json`，产物目录为 `target/generator-acceptance/cloud-IQi7Bn`。

认证后的额外检查确认 AI 菜单不存在，以下 AI 和 Workflow 接口均返回 HTTP 404，响应没有提交的 secret marker：

```text
/admin/ai/documents/list
/admin/ai/config
/admin/ai/chat
/admin/ai/documents/1
/admin/workflow/task/todo/page
/admin/workflow/form/list
```

Nacos 服务列表只有 `bixi-auth`、`bixi-gateway`、`bixi-generator`、`bixi-monitor`、`bixi-upms-biz`，没有 `bixi-ai-biz` 或 `bixi-workflow-biz`。新库 `ACT_*` 表数量为 `0`；预置的 Workflow 扩展表若存在也不代表引擎启动。

## Single disabled

隔离项目：`bixi-phase2-single-disabled-audit`。本轮端口为 `28183`（frontend）、`29994`（single）、`23319`（MySQL）和 `26392`（Redis）；RabbitMQ `25686`、Nacos `28861` 未监听。Single 容器 JVM 使用 `-Xms128m -Xmx384m`，镜像构建时间为 `2026-09-26T15:38:41Z`。

核心验收命令退出码为 `0`：

```text
BIXI_MODE=single \
  BIXI_ENV_FILE=/tmp/bixi-phase2-single-disabled-audit.env \
  node scripts/acceptance.mjs
```

结果覆盖登录、管理员角色和 `demo_task_{view,add,edit,del}` 权限、示例任务 CRUD、参数校验、三条操作审计，以及 Workflow 菜单隐藏和 Workflow 端点 404。原始摘要为 `target/phase2-runtime/single-disabled-audit-acceptance.json`。

同一栈的 Generator 黑盒于当前运行实例再次执行并退出码为 `0`：

```text
BIXI_MODE=single \
  BIXI_ENV_FILE=/tmp/bixi-phase2-single-disabled-audit.env \
  node scripts/generator-acceptance.mjs \
    --mode single \
    --env-file /tmp/bixi-phase2-single-disabled-audit.env \
    --output-dir target/phase2-runtime/single-disabled-audit-generator-output-rerun
```

单表 `sys_public_param` 为 17/17 preview/ZIP 条目和 9 个 XLSX 条目；父子表 `sys_dict/sys_dict_item` 为 21/21 preview/ZIP 条目和 9 个 XLSX 条目。服务端生成、同步、过期模板版本拒绝、权限、匿名拒绝和审计均通过。结构化摘要为 `target/phase2-runtime/single-disabled-audit-generator.json`，产物目录为 `target/phase2-runtime/single-disabled-audit-generator-output-rerun`。

Single 认证检查还确认 Workflow 菜单不存在，以下 AI 和 Workflow 接口均返回 HTTP 404，secret marker 未回显：

```text
/admin/ai/documents/list
/admin/ai/config
/admin/ai/chat
/admin/ai/documents/1
/admin/workflow/task/todo/page
/admin/workflow/form/list
```

此前使用的旧 Single 镜像曾返回包含 `/ai` 的菜单路径，而 `/workflow`、`/demo/leave/index` 不存在；该历史结果暴露了镜像或缓存复验差异，不能代表当前源码对齐的 disabled 运行态。源码中的 `SysMenuServiceImpl` 已按 `ai.enabled` 过滤并把该开关纳入菜单缓存 key。运行时环境确认 `AI_ENABLED=false`、`WORKFLOW_ENABLED=false`、`BIXI_DEPLOYMENT_MODE=single`；当前实例的复核结果见下节。

Single 数据库检查返回 `ACT_*` 表数量 `0`，`wf_process_instance` 和 `wf_business_task` 也未创建；应用未启动 Workflow 引擎、RabbitMQ 或 Nacos。

### Single disabled AI menu recheck (2026-09-27)

The earlier `/ai` menu observation above came from an older isolated image and is superseded by a fresh check against
the source-aligned Single process already running on port `29992` (PID `10693`; the process was not restarted or
cleared). Its runtime configuration was `AI_ENABLED=false`, `AI_CHAT_ENABLED=true`, `AI_EMBEDDING_ENABLED=true`,
`WORKFLOW_ENABLED=false`, and `BIXI_DEPLOYMENT_MODE=single`.

Using a real administrator token, `GET /admin/menu` returned HTTP `200` with API `code=0`. A recursive inspection of
the complete response found no menu entry whose `path` is `/ai` or starts with `/ai/`, and no entry whose permission
starts with `ai_` or `ai:`:

```text
pid: 10693
port: 29992
menu status: 200
api code: 0
menu top-level entries: 5
AI menu entries: []
```

This confirms the `SysMenuServiceImpl.isMenuAvailable` `ai.enabled` predicate and its cache-key variant in a real
Single disabled runtime. The prior stale-image result remains useful as a regression clue, but must not be used to
claim that the current disabled runtime exposes the AI menu. A credential-free JSON summary is preserved at
`target/c-blackbox/ai-menu-disabled-29992.json`.

## Cleanup and limits

本证据只引用上述临时项目；项目完成复验后应执行：

```text
docker compose \
  --env-file /tmp/bixi-phase2-single-disabled-audit.env \
  --project-name bixi-phase2-single-disabled-audit \
  -f compose.yaml --profile single down --remove-orphans
```

本矩阵证明核心 disabled 行为、Generator 黑盒和 Workflow/AI API 缺失行为。它不证明 AI provider/RAG enabled、生成项目自身 CRUD 运行、完整应用容器重启恢复、关闭期间消息积压/重新启用恢复，或 Cloud Quartz/通知专用黑盒。
