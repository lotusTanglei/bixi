# Bixi 数据库文档

## 📊 数据库概览

**数据库类型**: MySQL 8.0+
**字符集**: utf8mb4
**排序规则**: utf8mb4_unicode_ci
**总表数**: 36 个

### 表分布

| 模块 | 表前缀 | 表数量 | 说明 |
|------|--------|--------|------|
| 核心系统 | `sys_*` | 16 | 用户、角色、菜单、权限、字典等 |
| 工作流 | `wf_*` | 4 | 流程定义、实例、审批记录等 |
| 表单管理 | `wf_form`, `sys_form_permission` 等 | 5 | 动态表单、表单版本、表单权限等 |
| AI 模块 | `ai_*` | 5 | 会话、消息、文档、向量等 |
| 代码生成 | `gen_*` | 5 | 数据源、字段配置、模板等 |
| 定时任务 | `qrtz_*` | 3 | Quartz 定时任务框架表 |

---

## 🚀 快速开始

### ⚡ 一键初始化（推荐）

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE bixi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 导入表结构
mysql -u root -p bixi < 01_init_all_tables.sql

# 3. 导入初始数据（重要！）
mysql -u root -p bixi < 04_init_data.sql
```

**⚠️ 重要提示**：
- ✅ **必须执行 `04_init_data.sql`**，否则系统无法正常使用
- ✅ 初始数据包含：默认管理员、角色、菜单、字典、代码生成器模板
- ✅ 默认管理员账号：`admin` / `admin123`（首次登录后请立即修改）

### 📝 分步初始化（可选）

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE bixi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 导入表结构（按顺序）
mysql -u root -p bixi < 01_init_all_tables.sql   # 所有表结构
mysql -u root -p bixi < 02_add_constraints.sql  # 约束（可选）
mysql -u root -p bixi < 03_add_indexes.sql      # 索引（可选）

# 3. 导入初始数据（必须）
mysql -u root -p bixi < 04_init_data.sql        # 系统初始数据
```

---

## 📖 文档目录

```
sql/
├── README.md                      # 本文件（数据库概览和初始化指南）
├── DATABASE.md                    # 数据库设计文档
├── DATA_DICTIONARY.md             # 数据字典
├── 01_init_all_tables.sql        # 完整初始化脚本（表结构）
├── 02_add_constraints.sql        # 约束优化（可选）
├── 03_add_indexes.sql            # 索引优化（可选）
├── 04_init_data.sql              # ⭐ 初始数据（必须执行！）
├── bixi.sql                      # 核心系统模块（16 个表）
├── bixi_ai.sql                   # AI 模块（5 个表）
├── bixi_workflow.sql             # 工作流模块（4 个表）
├── bixi_form.sql                 # 表单模块（5 个表）
├── bixi_gen.sql                  # 代码生成器（5 个表）
├── bixi_job.sql                  # 定时任务（3 个表）
├── update_generator_templates.sql # 代码生成器模板更新脚本
└── UPDATE_TEMPLATES_GUIDE.md      # 模板更新执行指南
```

---

## 🎯 核心设计理念

### 1. 多租户支持

**所有表都包含 `tenant_id` 字段**，支持 SaaS 多租户模式：

```sql
`tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户id'
```

**隔离策略**：
- 通过 MyBatis Plus 拦截器实现数据隔离
- 查询时自动添加 `tenant_id` 条件
- 支持租户级数据备份和恢复

### 2. 逻辑删除

**所有表都包含 `del_flag` 字段**，实现软删除：

```sql
`del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标志（0正常 1删除）'
```

**优点**：
- ✅ 数据可恢复
- ✅ 保留审计记录
- ✅ 支持数据统计分析

### 3. 审计字段

**所有表都包含完整的审计字段**：

```sql
create_by BIGINT       -- 创建人
update_by BIGINT       -- 修改人
create_time DATETIME   -- 创建时间
update_time DATETIME   -- 修改时间
```

**用途**：
- 完整的操作记录
- 便于问题追溯
- 支持合规要求

### 4. 主键设计

**统一使用 `BIGINT` 作为主键类型**：

```sql
id BIGINT NOT NULL COMMENT '主键ID'
```

**主键生成策略**：
- 使用雪花算法（Snowflake）
- 保证分布式环境下 ID 唯一性
- ID 长度 19 位

---

## 🔗 表关系说明

### RBAC 权限模型

```
sys_user (用户)
  ├── N:M sys_role (角色) ←────┐
  │         ↑                   │
  │         │ 通过              │
  │         │ sys_user_role     │
  │         │                   │
  ├── N:M sys_menu (菜单) ──────┴── 通过 sys_role_menu
  │
  └── N:1 sys_dept (部门)
```

**关键关系**：
- 用户-角色：多对多（`sys_user_role`）
- 角色-菜单：多对多（`sys_role_menu`）
- 用户-部门：多对一（`sys_user.dept_id` → `sys_dept.id`）

### 工作流模型

```
wf_process_definition (流程定义)
  └── 1:N wf_process_instance (流程实例)
        ├── 1:N wf_task (任务，Flowable原生表)
        └── 1:N wf_approval_record (审批记录)
```

**扩展说明**：
- 项目扩展了 Flowable 原生表
- 通过 `process_definition_id`、`process_instance_id` 关联
- 支持中国式审批（转办、委派、驳回、会签）

### AI 模块关系

```
ai_session (会话)
  └── 1:N ai_message (消息)

ai_document (文档)
  └── 1:N ai_embedding (向量嵌入)
```

**使用场景**：
- `ai_session` + `ai_message`：实时对话
- `ai_document` + `ai_embedding`：RAG 知识库

---

## 📝 数据字典

详细的数据字典请查看：[DATA_DICTIONARY.md](DATA_DICTIONARY.md)

### 通用枚举值

| 字段 | 值 | 含义 |
|------|-----|------|
| `del_flag` | 0 | 正常 |
| `del_flag` | 1 | 已删除 |
| `status` | 0 | 正常/启用 |
| `status` | 1 | 停用/禁用 |
| `status` | 2 | 审核中 |

---

## ⚙️ 配置说明

### MySQL 配置要求

**最低版本**: MySQL 8.0+

**推荐配置** (`my.cnf`):
```ini
[mysqld]
# 字符集
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# 连接数
max_connections=500

# 缓存
innodb_buffer_pool_size=1G

# 日志
slow_query_log=1
slow_query_log_file=/var/log/mysql/slow.log
long_query_time=2
```

### 应用配置

**数据源配置** (`application.yml`):
```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/bixi?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8
    username: root
    password: your-password
```

---

## 🔍 索引优化

**基础索引已包含在初始化脚本中**，更多性能优化请查看：[03_add_indexes.sql](03_add_indexes.sql)

**常用查询索引**：
```sql
-- 用户表常用查询
CREATE INDEX idx_user_phone ON sys_user(phone);
CREATE INDEX idx_user_email ON sys_user(email);

-- 日志表时间范围查询
CREATE INDEX idx_log_type_date ON sys_log(type, create_time);

-- AI 消息会话查询
CREATE INDEX idx_msg_session_time ON ai_message(session_id, create_time DESC);
```

---

## 🔒 约束说明

**唯一约束**（可选执行，见 [02_add_constraints.sql](02_add_constraints.sql)）：
```sql
-- 用户表唯一约束
ALTER TABLE sys_user ADD CONSTRAINT uk_username UNIQUE (username);
ALTER TABLE sys_user ADD CONSTRAINT uk_phone UNIQUE (phone);
ALTER TABLE sys_user ADD CONSTRAINT uk_email UNIQUE (email);

-- 字典表唯一约束
ALTER TABLE sys_dict ADD CONSTRAINT uk_dict_type UNIQUE (type);
```

**外键约束**：
- 当前版本未启用外键约束（由应用层保证数据完整性）
- 如需启用，请参考 [02_add_constraints.sql](02_add_constraints.sql)

---

## 📦 数据库维护

### 备份

```bash
# 完整备份
mysqldump -u root -p bixi > bixi_backup_$(date +%Y%m%d).sql

# 仅备份结构
mysqldump -u root -p --no-data bixi > bixi_schema.sql

# 仅备份数据
mysqldump -u root -p --no-create-info bixi > bixi_data.sql
```

### 恢复

```bash
mysql -u root -p bixi < bixi_backup_20260328.sql
```

### 性能分析

```sql
-- 查看慢查询
SELECT * FROM mysql.slow_log ORDER BY query_time DESC LIMIT 10;

-- 分析表
ANALYZE TABLE sys_user;

-- 优化表
OPTIMIZE TABLE sys_log;
```

---

## 🛠️ 故障排查

### 常见问题

**1. 字符集问题**
```sql
-- 检查字符集
SHOW VARIABLES LIKE 'character%';
SHOW VARIABLES LIKE 'collation%';

-- 修改数据库字符集
ALTER DATABASE bixi CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**2. 连接数不足**
```sql
-- 查看当前连接数
SHOW STATUS LIKE 'Threads_connected';

-- 查看最大连接数
SHOW VARIABLES LIKE 'max_connections';

-- 修改最大连接数（临时）
SET GLOBAL max_connections=500;
```

**3. 慢查询优化**
```sql
-- 启用慢查询日志
SET GLOBAL slow_query_log=1;
SET GLOBAL long_query_time=2;
```

---

## 📚 更多文档

- [数据库设计文档](DATABASE.md) - 详细的表结构和设计说明
- [数据字典](DATA_DICTIONARY.md) - 所有字段的详细说明
- [性能优化指南](#) - TODO: 待创建
- [升级指南](#) - TODO: 待创建

---

## 🤝 贡献

如需修改数据库结构，请遵循以下流程：

1. **修改 SQL 脚本**：
   - 在相应的 `.sql` 文件中添加变更
   - 或创建新的版本脚本（如：`V1.0.1__add_xxx.sql`）

2. **更新文档**：
   - 更新 `DATABASE.md` 中的表结构说明
   - 更新 `DATA_DICTIONARY.md` 中的字段说明
   - 更新 ER 图（如有变更）

3. **测试验证**：
   - 在本地测试 SQL 脚本可正确执行
   - 验证应用程序可正常运行

---

## 📄 许可证

[MIT License](../../LICENSE)

---

**最后更新**: 2026-03-28
**维护者**: Bixi Team
