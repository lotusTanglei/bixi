# SQL 脚本

数据库初始化与模块 SQL 脚本集合，包含建表、约束、索引和初始数据。

## 目录说明

本目录包含 Bixi 项目所有数据库脚本，按执行顺序和模块组织。

## 脚本清单

| 文件 | 说明 |
|------|------|
| `01_init_all_tables.sql` | 全量建表脚本 |
| `02_add_constraints.sql` | 添加约束（外键、唯一等） |
| `03_add_indexes.sql` | 添加索引 |
| `04_init_data.sql` | 初始化基础数据 |
| `bixi.sql` | UPMS 核心模块表结构 |
| `bixi_ai.sql` | AI 模块表结构 |
| `bixi_gen.sql` | 代码生成器表结构 |
| `bixi_job.sql` | 定时任务表结构 |
| `bixi_workflow.sql` | 工作流模块表结构 |
| `bixi_form.sql` | 表单引擎表结构 |
| `DATABASE.md` | 数据库设计文档 |
| `DATA_DICTIONARY.md` | 数据字典 |

## 使用方式

1. 首次部署：按 `01` → `02` → `03` → `04` 顺序执行
2. 单模块部署：直接执行对应模块的 SQL 文件（如 `bixi_ai.sql`）
