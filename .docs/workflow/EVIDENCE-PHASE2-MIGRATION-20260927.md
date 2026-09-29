# Phase 2 全量迁移实测证据

更新时间：2026-09-27。

## 环境

- 独立 Compose 项目：`bixi-phase2-migration`
- MySQL 镜像：`mysql:8.4.3`
- 数据库为空库，由 `compose.yaml` 的初始化脚本建立基础 schema/data
- 未停止或修改现有 `bixi-phase2-cloud`、`bixi-phase2-clean` 及其他运行实例

## 结果

```text
BIXI_ENV_FILE=/tmp/bixi-phase2-migration.env \
  bash scripts/migrate-workflow-schema.sh --scope phase2
  Controlled phase2 schema migration passed.

# 第二次执行同一命令
  Controlled phase2 schema migration passed.
  bixi_schema_migration rows: 35
```

首轮按文件名顺序执行全部 **35** 个 Phase 2 增量脚本；第二轮对 35 个已登记批次全部执行跳过，未重复修改 schema 或数据。脚本保留 `WORKFLOW_SCHEMA_UPDATE=false`、维护窗口和显式确认值保护。

预览命令 `make phase2-migration-list` 输出 35 个迁移路径（Make 输出本身另有 1 行命令回显，因此终端总行数为 36）。

## 范围与限制

本证据证明空白数据库上的完整 Phase 2 迁移顺序和幂等登记行为。它不替代生产数据量、跨版本升级、自动回滚或数据库高可用演练；这些仍按运行运维清单单独验证。
