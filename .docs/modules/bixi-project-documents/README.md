# bixi-project-documents — 项目文档与 SQL 脚本

存放数据库初始化脚本、数据字典文档和部署工具脚本。

## 目录结构

```
bixi-project-documents/
├── sql/
│   ├── 01_init_all_tables.sql   # 全量建表脚本（所有模块表结构）
│   ├── 02_add_constraints.sql   # 约束定义（外键、唯一约束等）
│   ├── 03_add_indexes.sql       # 索引创建脚本
│   ├── 04_init_data.sql         # 初始化种子数据（菜单、字典、默认用户等）
│   ├── bixi.sql                 # UPMS 模块独立 SQL
│   ├── bixi_ai.sql              # AI 模块独立 SQL
│   ├── bixi_form.sql            # 表单模块独立 SQL
│   ├── bixi_gen.sql             # 代码生成模块独立 SQL
│   ├── bixi_job.sql             # 定时任务模块独立 SQL
│   ├── bixi_workflow.sql        # 工作流模块独立 SQL
│   ├── DATABASE.md              # 数据库设计文档
│   ├── DATA_DICTIONARY.md       # 数据字典
│   └── README.md                # SQL 脚本使用说明
└── start-tools.sh               # 部署工具脚本
```

## 使用说明

- 首次部署按编号顺序执行 `01_` ~ `04_` 脚本完成数据库初始化
- 各模块独立 SQL 文件可用于单独初始化对应模块的表结构
- `DATA_DICTIONARY.md` 记录所有表和字段的详细说明
