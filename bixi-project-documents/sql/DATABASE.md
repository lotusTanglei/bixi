# Bixi 数据库设计文档

## 📊 数据库概览

**数据库类型**: MySQL 8.0+
**字符集**: utf8mb4
**排序规则**: utf8mb4_unicode_ci
**总表数**: 36 个

### 表分布

| 模块 | 表前缀 | 表数量 | 说明 |
|------|--------|--------|------|
| 核心系统 | `sys_*` | 16 | 用户、角色、菜单、权限、字典、日志等 |
| 工作流 | `wf_*` | 8 | 流程定义、实例、审批、表单等 |
| AI 模块 | `ai_*` | 5 | 会话、消息、文档、向量等 |
| 代码生成 | `gen_*` | 5 | 数据源、字段配置、模板等 |
| 定时任务 | `qrtz_*` | 3 | Quartz 定时任务框架表 |

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
- 适用于 SaaS 多租户应用场景

**优势**：
- ✅ 单实例服务多客户
- ✅ 数据天然隔离
- ✅ 降低运营成本

### 2. 逻辑删除

**所有表都包含 `del_flag` 字段**，实现软删除：

```sql
`del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标志（0正常 1删除）'
```

**优点**：
- ✅ 数据可恢复（误删除可找回）
- ✅ 保留审计记录（追溯历史操作）
- ✅ 支持数据统计分析（不删除历史数据）

**实现方式**：
- 删除操作：`UPDATE ... SET del_flag = '1'`
- 查询操作：`WHERE del_flag = '0'`
- 物理删除：定期清理任务清理 `del_flag = '1'` 的数据

### 3. 审计字段

**所有表都包含完整的审计字段**：

```sql
create_by BIGINT       -- 创建人
update_by BIGINT       -- 修改人
create_time DATETIME   -- 创建时间
update_time DATETIME   -- 修改时间
```

**用途**：
- 完整的操作记录（谁在什么时候创建/修改了数据）
- 便于问题追溯（排查问题时查看操作历史）
- 支持合规要求（审计要求）
- 数据恢复和回滚

### 4. 主键设计

**统一使用 `BIGINT` 作为主键类型**：

```sql
id BIGINT NOT NULL COMMENT '主键ID'
```

**主键生成策略**：
- 使用**雪花算法（Snowflake）**生成主键
- 保证分布式环境下 ID 唯一性
- ID 长度 19 位数字字符串
- 趋势递增（按时间递增，有利于索引性能）

**为什么不用自增 ID**：
- ❌ 自增 ID 在分布式环境下容易冲突
- ❌ 分库分表时需要额外的 ID 生成机制
- ❌ ID 可被预测（安全风险）

**为什么不用 UUID**：
- ❌ UUID 无序，影响索引性能
- ❌ UUID 太长（36 字符），占用存储空间
- ❌ 不利于排序和范围查询

---

## 🔗 表关系说明

### RBAC 权限模型

Bixi 采用标准的 RBAC（基于角色的访问控制）模型：

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

**关系说明**：
1. **用户-角色**：多对多（`sys_user_role` 关联表）
   - 一个用户可以有多个角色
   - 一个角色可以分配给多个用户

2. **角色-菜单**：多对多（`sys_role_menu` 关联表）
   - 一个角色可以访问多个菜单
   - 一个菜单可以分配给多个角色

3. **用户-部门**：多对一
   - 一个用户属于一个部门
   - 一个部门可以有多个用户

**数据权限**：
- 支持 4 种数据权限类型（`sys_role.ds_type`）：
  - `0` - 全部数据
  - `1` - 自定义数据
  - `2` - 本部门数据
  - `3` - 本部门及子部门数据
  - `4` - 仅本人数据

### 工作流模型

Bixi 扩展了 Flowable 工作流引擎，支持中国式审批：

```
wf_process_definition (流程定义)
  └── 1:N wf_process_instance (流程实例)
        ├── 1:N wf_task (任务，Flowable原生表 ACT_RU_TASK)
        └── 1:N wf_approval_record (审批记录)
```

**扩展说明**：
- 项目扩展了 Flowable 原生表
- 通过 `process_definition_id`、`process_instance_id` 关联
- 支持中国式审批：
  - **转办**（Transfer）：将任务转给其他人处理
  - **委派**（Delegate）：委托他人代为处理
  - **驳回**（Reject）：拒绝并退回到上一节点
  - **会签**（Countersign）：多人同时审批，全部通过才算通过
  - **或签**（Or Sign）：多人中任意一人通过即可

**与 Flowable 原生表的关系**：
- `wf_process_definition.process_definition_id` → `ACT_RE_PROCDEF.ID_`
- `wf_process_instance.process_instance_id` → `ACT_RU_EXECUTION.ID_`
- `wf_approval_record.task_id` → `ACT_RU_TASK.ID_`

### AI 模块关系

```
ai_session (会话)
  └── 1:N ai_message (消息)

ai_document (文档)
  └── 1:N ai_embedding (向量嵌入)
```

**使用场景**：
1. **实时对话**：`ai_session` + `ai_message`
   - 用于实时聊天场景
   - 支持流式响应
   - 保留完整对话历史

2. **RAG 知识库**：`ai_document` + `ai_embedding`
   - 用于知识库检索增强生成（RAG）
   - 文档向量化后存储到向量数据库
   - 支持相似度搜索

3. **对话历史归档**：`ai_conversation`
   - 用于历史对话查询
   - 支持会话统计和分析
   - 可用于模型训练数据

---

## 📐 数据库设计模式

### 1. 统一字段设计

**所有表都包含以下标准字段**：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID（雪花算法） |
| del_flag | CHAR(1) | 删除标志（0-正常，1-删除） |
| status | CHAR(1) | 状态（0-正常，1-停用） |
| tenant_id | VARCHAR(32) | 租户ID（多租户） |
| create_by | BIGINT | 创建人 |
| update_by | BIGINT | 修改人 |
| create_time | DATETIME | 创建时间（自动填充） |
| update_time | DATETIME | 修改时间（自动更新） |

**优势**：
- ✅ 统一的开发规范
- ✅ 便于代码生成
- ✅ 易于理解和维护

### 2. 命名规范

**表命名**：
- 使用小写字母和下划线
- 按模块前缀分组：`sys_*`、`wf_*`、`ai_*`、`gen_*`、`qrtz_*`
- 使用名词复数形式（可选）

**字段命名**：
- 使用小写字母和下划线
- 布尔值字段：`is_*`、`has_*`、`can_*`
- 时间字段：`*_time`（如：`create_time`）
- 状态字段：`*_status`（如：`audit_status`）
- ID 字段：`*_id`（如：`user_id`）

**索引命名**：
- 主键：`PRIMARY KEY`
- 唯一索引：`uk_*`（unique key）
- 普通索引：`idx_*`（index）
- 全文索引：`ftidx_*`（full text index）

**约束命名**：
- 外键：`fk_*`（foreign key）
- 检查约束：`ck_*`（check）
- 默认值：`df_*`（default）

### 3. 字段类型选择

| 数据类型 | 说明 | 示例 |
|---------|------|------|
| BIGINT | 主键、外键、数量 | `id`, `user_id` |
| VARCHAR(255) | 短文本、名称 | `username`, `title` |
| VARCHAR(500) | 描述、备注 | `description`, `remark` |
| TEXT | 长文本 | `content`, `params` |
| LONGTEXT | 超长文本 | `schema_json` |
| CHAR(1) | 状态、标志 | `status`, `del_flag` |
| DATETIME | 日期时间 | `create_time` |
| DECIMAL(10,2) | 金额 | `amount` |
| JSON | JSON 数据（MySQL 5.7.8+） | `params`, `sources` |

---

## 📈 性能优化

### 索引策略

**1. 主键索引**：
- 所有表都有主键索引
- 使用 `BIGINT` 类型，支持海量数据

**2. 唯一索引**：
- 用户名、手机号、邮箱等唯一字段
- 字典类型、角色编码等业务唯一字段

**3. 普通索引**：
- 外键字段（如：`dept_id`, `user_id`）
- 常用查询字段（如：`status`, `create_time`）
- 组合索引（如：`(status, del_flag)`）

**4. 覆盖索引**：
- 避免回表查询，提升性能
- 示例：`idx_user_login_cover(username, password, status, del_flag)`

**优化脚本**：详见 [03_add_indexes.sql](03_add_indexes.sql)

### 查询优化

**1. 避免 `SELECT *`**：
```sql
-- ❌ 不推荐
SELECT * FROM sys_user;

-- ✅ 推荐
SELECT id, username, real_name FROM sys_user;
```

**2. 使用索引字段查询**：
```sql
-- ✅ 使用索引字段
WHERE phone = '13800138000';

-- ❌ 避免在索引字段上使用函数
WHERE SUBSTRING(phone, 1, 3) = '138';
```

**3. 合理使用 `LIMIT`**：
```sql
-- 分页查询
SELECT * FROM sys_log
ORDER BY create_time DESC
LIMIT 10 OFFSET 0;
```

### 分区表（大数据量优化）

**建议**：当日志表（`sys_log`）数据量超过 1000 万时，考虑分区：

```sql
-- 按月分区
ALTER TABLE sys_log
PARTITION BY RANGE (YEAR(create_time) * 100 + MONTH(create_time)) (
    PARTITION p202601 VALUES LESS THAN (202602),
    PARTITION p202602 VALUES LESS THAN (202603),
    -- ...
    PARTITION pmax VALUES LESS THAN MAXVALUE
);
```

---

## 🔒 数据库安全

### 1. 最小权限原则

**应用账号权限**：
```sql
-- 创建只读账号（用于报表查询）
CREATE USER 'bixi_read'@'%' IDENTIFIED BY 'password';
GRANT SELECT ON bixi.* TO 'bixi_read'@'%';

-- 创建读写账号（用于应用）
CREATE USER 'bixi_app'@'%' IDENTIFIED BY 'password';
GRANT SELECT, INSERT, UPDATE, DELETE ON bixi.* TO 'bixi_app'@'%';
```

### 2. SQL 注入防护

**使用参数化查询**：
```java
// ✅ 推荐：MyBatis Plus 参数化
LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
wrapper.eq(SysUser::getUsername, username);

// ❌ 避免：字符串拼接
String sql = "SELECT * FROM sys_user WHERE username = '" + username + "'";
```

### 3. 敏感数据加密

**密码加密**：
```sql
-- 使用 BCrypt 加密存储
password VARCHAR(100)  -- 存储加密后的密码
```

**敏感字段加密**（可选）：
```sql
-- 手机号、身份证等敏感信息加密存储
phone_encrypted VARCHAR(255)  -- AES 加密
```

---

## 📦 数据库维护

### 备份策略

**全量备份**（每周一次）：
```bash
mysqldump -u root -p --single-transaction --routines --triggers \
  bixi > bixi_full_backup_$(date +%Y%m%d).sql
```

**增量备份**（每天一次）：
```bash
mysqldump -u root -p --single-transaction --where="create_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)" \
  bixi > bixi_incremental_backup_$(date +%Y%m%d).sql
```

### 数据清理

**定期清理逻辑删除的数据**（每月一次）：
```sql
-- 删除 90 天前删除的数据
DELETE FROM sys_user
WHERE del_flag = '1'
  AND update_time < DATE_SUB(NOW(), INTERVAL 90 DAY);
```

**定期清理日志数据**（每月一次）：
```sql
-- 删除 180 天前的日志
DELETE FROM sys_log
WHERE create_time < DATE_SUB(NOW(), INTERVAL 180 DAY);
```

### 性能监控

**慢查询日志**：
```sql
-- 启用慢查询日志
SET GLOBAL slow_query_log = 1;
SET GLOBAL long_query_time = 2;

-- 查看慢查询
SELECT * FROM mysql.slow_log
ORDER BY query_time DESC
LIMIT 10;
```

**表碎片整理**：
```sql
-- 分析表
ANALYZE TABLE sys_user;

-- 优化表（重建索引）
OPTIMIZE TABLE sys_user;
```

---

## 🚀 数据库升级

### 版本管理

**建议使用 Flyway 或 Liquibase** 进行数据库版本管理：

**Flyway 示例**：
```
bixi-gateway/src/main/resources/db/migration/
├── V1.0.0__init_schema.sql
├── V1.0.1__add_user_constraints.sql
├── V1.0.2__add_performance_indexes.sql
└── V1.0.3__add_ai_module_tables.sql
```

### 升级流程

1. **备份数据库**
   ```bash
   mysqldump -u root -p bixi > bixi_backup_before_upgrade.sql
   ```

2. **执行升级脚本**
   ```bash
   mysql -u root -p bixi < V1.0.1__add_user_constraints.sql
   ```

3. **验证升级结果**
   ```sql
   -- 检查表结构
   DESC sys_user;

   -- 检查约束
   SELECT * FROM information_schema.TABLE_CONSTRAINTS
   WHERE TABLE_SCHEMA = 'bixi';
   ```

4. **回滚（如需）**
   ```bash
   mysql -u root -p bixi < rollback_V1.0.1.sql
   ```

---

## 📚 相关文档

- [README.md](README.md) - 数据库概览和快速开始
- [DATA_DICTIONARY.md](DATA_DICTIONARY.md) - 详细的数据字典
- [01_init_all_tables.sql](01_init_all_tables.sql) - 完整初始化脚本
- [02_add_constraints.sql](02_add_constraints.sql) - 约束优化脚本
- [03_add_indexes.sql](03_add_indexes.sql) - 索引优化脚本

---

## 🤝 贡献指南

**修改数据库结构时**，请遵循以下流程：

1. **修改 SQL 脚本**：
   - 更新相应的 `.sql` 文件
   - 或创建新的版本脚本（如：`V1.0.1__add_xxx.sql`）

2. **更新文档**：
   - 更新本文档中的表结构说明
   - 更新 `DATA_DICTIONARY.md` 中的字段说明
   - 更新 ER 图（如有变更）

3. **测试验证**：
   - 在本地测试 SQL 脚本可正确执行
   - 验证应用程序可正常运行
   - 通过自动化测试（如有）

---

**最后更新**: 2026-03-28
**维护者**: Bixi Team
**许可证**: [MIT License](../../LICENSE)
