# SQL 脚本

本目录包含新库初始化脚本、模块独立脚本和既有数据库增量迁移。初始化脚本与迁移脚本的适用场景不同，执行前先确认数据库是否已有业务数据。

## 新库初始化

| 顺序 | 文件 | 作用 |
|---:|---|---|
| 1 | `01_schema.sql` | 创建所有模块的基础表 |
| 2 | `02_data.sql` | 写入菜单、字典、角色授权和其他基础数据 |
| 3 | `03_constraints.sql` | 添加外键、唯一约束等约束 |
| 4 | `04_indexes.sql` | 添加查询和唯一索引 |

新库按上表顺序执行。Compose 会在这四个脚本之后执行 `deploy/mysql/05_runtime_secrets.sh`，将 `.env` 中生成的管理员密码、租户默认密码和 OAuth client secret 写入数据库。`bixi.sql`、`bixi_ai.sql`、`bixi_form.sql`、`bixi_gen.sql`、`bixi_job.sql` 和 `bixi_workflow.sql` 是按模块拆分的独立脚本，按需使用时应先确认与目标库的表结构兼容。

文件名就是执行顺序：先建表，再装载种子数据，然后添加约束，最后创建索引。`03_constraints.sql` 会校验已有数据；其中菜单和部门使用 `-1`、`0` 等根节点哨兵值，所以脚本不会添加可能拒绝这些根记录的自引用外键。

## 既有库迁移

`migrations/` 保存按日期和名称排序的增量脚本，覆盖租户、权限、Workflow/Flowable、可靠投递、Quartz、生成器、AI/RAG、通知和示例业务等演进。迁移脚本按文件登记到 `bixi_schema_migration`，同一文件成功后可跳过重复执行。

完整前置条件、迁移顺序和各模块的维护窗口要求见 [`migrations/README.md`](../../../../bixi-project-documents/sql/migrations/README.md)。常用入口：

```bash
make phase2-migration-list

BIXI_ENV_FILE=/path/to/maintenance.env \
BIXI_SCHEMA_MAINTENANCE=true \
BIXI_MIGRATION_CONFIRM=APPLY_PHASE2_MIGRATIONS \
make phase2-schema-migrate
```

已有数据库不要重新执行 `01_schema.sql` 到 `04_indexes.sql`。迁移失败时保留已执行的 DDL 和迁移账本，修复报告的结构冲突后按迁移说明重试。

`make phase2-schema-migrate` 和 `make workflow-schema-migrate` 通过 Docker Compose 执行；直接打包部署时停止应用和消费者，使用 MySQL CLI 按 [`migrations/README.md`](../../../../bixi-project-documents/sql/migrations/README.md) 的文件名顺序逐个执行，并在 `bixi_schema_migration` 登记成功文件。

## 设计文档

- [`DATABASE.md`](../../../../bixi-project-documents/sql/DATABASE.md)：数据库结构和模块关系。
- [`DATA_DICTIONARY.md`](../../../../bixi-project-documents/sql/DATA_DICTIONARY.md)：表、字段和取值说明。
