# bixi-project-documents — 数据库文档与 SQL 脚本

本目录维护 Bixi 的数据库初始化脚本、增量迁移脚本、数据库说明和数据字典。脚本与后端实体、Mapper 和运行配置共同构成数据库契约。

## 目录结构

```text
bixi-project-documents/
├── README.md
└── sql/
    ├── 01_schema.sql             # 新库全量建表
    ├── 02_data.sql               # 菜单、字典和基础数据
    ├── 03_constraints.sql        # 约束
    ├── 04_indexes.sql            # 索引
    ├── bixi.sql                     # UPMS 独立脚本
    ├── bixi_ai.sql                  # AI 独立脚本
    ├── bixi_form.sql                # 表单独立脚本
    ├── bixi_gen.sql                 # 生成器独立脚本
    ├── bixi_job.sql                 # Quartz 独立脚本
    ├── bixi_workflow.sql            # Workflow 独立脚本
    ├── DATABASE.md                 # 数据库设计说明
    ├── DATA_DICTIONARY.md          # 表和字段说明
    ├── README.md                   # SQL 使用说明
    └── migrations/                 # 既有数据库的受控增量迁移
        └── README.md               # 迁移顺序、前置条件和回滚边界
```

## 使用边界

- 新建数据库按 [`sql/README.md`](../../../bixi-project-documents/sql/README.md) 中的 `01` → `02` → `03` → `04` 顺序执行；Compose 还会最后执行 `05_runtime_secrets.sh` 写入运行时密码和 OAuth client secret。
- 四个初始化文件名现在就是执行顺序：`01_schema.sql` 建表、`02_data.sql` 装载种子数据、`03_constraints.sql` 添加约束、`04_indexes.sql` 创建索引。
- 既有数据库先阅读 [`sql/migrations/README.md`](../../../bixi-project-documents/sql/migrations/README.md)，按维护窗口和模块依赖执行增量迁移；不要重放全量初始化脚本。
- 模块独立 SQL 适合按需初始化对应模块，使用前应确认目标库和已有表结构，避免与全量脚本重复建表。
- 迁移脚本会对已有列、索引、菜单或权限做结构校验；发现冲突时应停止并人工处理，不要用全量脚本覆盖业务数据。

## 相关命令

```bash
# 查看当前受控迁移批次
make phase2-migration-list

# 在停止应用写入的维护窗口执行全量 Phase 2 迁移
BIXI_ENV_FILE=/path/to/maintenance.env \
BIXI_SCHEMA_MAINTENANCE=true \
BIXI_MIGRATION_CONFIRM=APPLY_PHASE2_MIGRATIONS \
make phase2-schema-migrate
```

只维护 Workflow/Rabbit 批次时使用 `make workflow-schema-migrate`，具体脚本顺序、数据库权限、Redis 权限缓存清理和回退边界以 [`sql/migrations/README.md`](../../../bixi-project-documents/sql/migrations/README.md) 为准。
