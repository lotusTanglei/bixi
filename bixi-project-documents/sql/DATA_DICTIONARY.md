# Bixi 数据字典

本文档说明 Bixi 项目中所有字段的含义、类型和枚举值。

---

## 📋 目录

- [通用枚举值](#通用枚举值)
- [核心系统模块 (sys_*)](#核心系统模块-sys_)
- [工作流模块 (wf_*)](#工作流模块-wf_)
- [AI 模块 (ai_*)](#ai-模块-ai_)
- [代码生成器模块 (gen_*)](#代码生成器模块-gen_)
- [定时任务模块 (qrtz_*)](#定时任务模块-qrtz_)

---

## 通用枚举值

### del_flag（删除标志）

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | 正常 | 数据正常使用 |
| 1 | 已删除 | 逻辑删除，数据不可见 |

**使用场景**：所有表的软删除标记

---

### status（状态）

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | 正常/启用 | 数据正常、功能启用 |
| 1 | 停用/禁用 | 暂停使用、功能禁用 |
| 2 | 审核中 | 待审核（特定业务） |
| 9 | 异常 | 系统异常（如：日志类型） |

**使用场景**：用户状态、角色状态、菜单状态等

---

### data_status（数据状态）

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | 正常 | 数据正常 |
| 1 | 待割接 | 准备数据迁移 |
| 2 | 已归档 | 历史归档数据 |

**使用场景**：数据生命周期管理

---

### visible（可见性）

| 值 | 含义 | 说明 |
|----|------|------|
| 0 | 隐藏 | 菜单或按钮隐藏 |
| 1 | 显示 | 菜单或按钮显示 |

**使用场景**：菜单可见性、按钮显示控制

---

## 核心系统模块 (sys_*)

### sys_user（用户表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | 雪花算法生成 |
| username | VARCHAR(50) | 用户账号 | 唯一，登录用 |
| password | VARCHAR(100) | 密码 | BCrypt 加密 |
| phone | VARCHAR(11) | 手机号 | 唯一 |
| email | VARCHAR(100) | 邮箱 | 唯一 |
| real_name | VARCHAR(50) | 真实姓名 | |
| nickname | VARCHAR(50) | 昵称 | |
| avatar | VARCHAR(500) | 头像 | URL |
| dept_id | BIGINT | 部门ID | 外键：sys_dept |
| role_id | BIGINT | 角色ID | 外键：sys_role |
| lock_flag | CHAR(1) | 锁定标志 | 0-正常，9-锁定 |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

**索引**：
- PRIMARY KEY (`id`)
- UNIQUE KEY `uk_username` (`username`)
- UNIQUE KEY `uk_phone` (`phone`)
- UNIQUE KEY `uk_email` (`email`)
- INDEX `idx_dept_id` (`dept_id`)

---

### sys_role（角色表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| name | VARCHAR(64) | 角色名称 | |
| code | VARCHAR(64) | 角色编码 | 索引字段，如：ROLE_ADMIN |
| description | VARCHAR(255) | 角色描述 | |
| sn | INT | 排序 | |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| data_status | CHAR(1) | 数据状态 | 用来标识数据状态 |
| tenant_id | BIGINT | 租户ID | |
| remark | VARCHAR(500) | 备注 | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

---

### sys_menu（菜单表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| name | VARCHAR(32) | 菜单名称 | |
| en_name | VARCHAR(128) | 英文名称 | |
| permission | VARCHAR(32) | 权限标识 | 如：sys_user_add |
| path | VARCHAR(128) | 路由路径 | 前端路由 |
| parent_id | BIGINT | 父菜单ID | 0-顶级菜单 |
| icon | VARCHAR(64) | 菜单图标 | Element Plus 图标名 |
| visible | CHAR(1) | 是否可见 | 0-隐藏，1-显示 |
| sn | INT | 排序值 | 越小越靠前 |
| keep_alive | CHAR(1) | 是否缓存 | 0-否，1-是 |
| embedded | CHAR(1) | 是否内嵌 | 0-否，1-是 |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

---

### sys_dept（部门表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| name | VARCHAR(50) | 部门名称 | |
| code | VARCHAR(50) | 部门编码 | 唯一 |
| sn | INT | 排序 | |
| leader | BIGINT | 负责人 | 外键：sys_user |
| parent_id | BIGINT | 父级部门ID | 0-顶级部门 |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

---

### sys_dict（字典表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| type | VARCHAR(100) | 字典类型 | 如：sys_user_sex |
| name | VARCHAR(100) | 字典名称 | |
| description | VARCHAR(200) | 字典描述 | |
| sn | INT | 排序号 | |
| system_flag | CHAR(1) | 系统标志 | 0-业务字典，1-系统字典 |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

---

### sys_dict_item（字典项表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| dict_id | BIGINT | 字典ID | 外键：sys_dict |
| value | VARCHAR(100) | 字典项值 | |
| label | VARCHAR(100) | 字典项标签 | 显示文本 |
| dict_type | VARCHAR(100) | 字典类型 | 冗余字段，便于查询 |
| description | VARCHAR(100) | 字典项描述 | |
| sn | INT | 排序 | |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

---

### sys_log（日志表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 编号 | |
| type | CHAR(1) | 日志类型 | 0-登录，1-操作，9-异常 |
| title | VARCHAR(255) | 日志标题 | |
| service_id | VARCHAR(32) | 服务ID | 微服务名称 |
| remote_addr | VARCHAR(255) | 远程地址 | IP 地址 |
| user_agent | VARCHAR(1000) | 用户代理 | 浏览器信息 |
| request_uri | VARCHAR(255) | 请求URI | |
| method | VARCHAR(10) | 请求方法 | GET/POST/PUT/DELETE |
| params | TEXT | 请求参数 | JSON 格式 |
| time | BIGINT | 执行时间 | 毫秒 |
| exception | TEXT | 异常信息 | |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

**日志类型枚举**：
- `0` - 登录日志
- `1` - 操作日志
- `9` - 异常日志

---

### sys_file（文件管理表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 编号 | |
| name | VARCHAR(100) | 文件名 | 存储后的文件名 |
| bucket | VARCHAR(200) | 文件存储桶名称 | OSS Bucket |
| original | VARCHAR(100) | 原始文件名 | 上传时的文件名 |
| type | VARCHAR(50) | 文件类型 | MIME 类型 |
| size | BIGINT | 文件大小 | 字节 |
| source | VARCHAR(100) | 文件来源 | minio/aliyun/腾讯 |
| business | VARCHAR(100) | 所属业务 | 业务模块标识 |
| source_id | BIGINT | 文件来源id | 关联业务ID |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建人 | |
| update_by | BIGINT | 修改人 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 修改时间 | |

**文件来源枚举**：
- `minio` - MinIO 对象存储
- `aliyun` - 阿里云 OSS
- `tencent` - 腾讯云 COS
- `qcloud` - 腾讯云 COS（旧称）

---

## 工作流模块 (wf_*)

### wf_process_definition（流程定义扩展表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| process_definition_id | VARCHAR(64) | Flowable流程定义ID | 关联 Flowable 原生表 |
| process_key | VARCHAR(64) | 流程标识 | 唯一，如：leave_approval |
| process_name | VARCHAR(255) | 流程名称 | |
| category | VARCHAR(64) | 流程分类 | |
| version | INT | 版本号 | |
| description | VARCHAR(500) | 描述 | |
| form_key | VARCHAR(255) | 表单Key | 关联 wf_form |
| diagram_resource_name | VARCHAR(255) | 流程图资源名 | BPMN 文件名 |
| suspension_state | INT | 挂起状态 | 1-激活，0-挂起 |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

---

### wf_process_instance（流程实例扩展表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| process_instance_id | VARCHAR(64) | Flowable流程实例ID | 关联 Flowable 原生表 |
| process_definition_id | VARCHAR(64) | 流程定义ID | |
| process_key | VARCHAR(64) | 流程标识 | |
| business_key | VARCHAR(255) | 业务Key | 关联业务数据 |
| business_table | VARCHAR(128) | 业务表名 | |
| business_id | BIGINT | 业务ID | |
| title | VARCHAR(255) | 流程标题 | |
| start_user_id | BIGINT | 发起人ID | |
| start_user_name | VARCHAR(64) | 发起人姓名 | |
| status | VARCHAR(32) | 流程状态 | running/completed/terminated |
| end_time | DATETIME | 结束时间 | |
| duration | BIGINT | 耗时毫秒 | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**流程状态枚举**：
- `running` - 运行中
- `completed` - 已完成
- `terminated` - 已终止

---

### wf_approval_record（审批记录表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| process_instance_id | VARCHAR(64) | 流程实例ID | |
| task_id | VARCHAR(64) | 任务ID | Flowable 任务ID |
| task_name | VARCHAR(255) | 任务名称 | |
| task_key | VARCHAR(64) | 任务Key | |
| approval_type | VARCHAR(32) | 审批类型 | approve/reject/transfer/delegate |
| approval_user_id | BIGINT | 审批人ID | |
| approval_user_name | VARCHAR(64) | 审批人姓名 | |
| approval_comment | VARCHAR(1000) | 审批意见 | |
| approval_time | DATETIME | 审批时间 | |
| delegate_user_id | BIGINT | 被委托人ID | |
| delegate_user_name | VARCHAR(64) | 被委托人姓名 | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**审批类型枚举**：
- `approve` - 同意
- `reject` - 拒绝
- `transfer` - 转办
- `delegate` - 委托

---

### wf_category（流程分类表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| category_name | VARCHAR(128) | 分类名称 | |
| category_code | VARCHAR(64) | 分类编码 | 唯一 |
| parent_id | BIGINT | 父分类ID | 0-顶级分类 |
| sn | INT | 排序号 | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

---

### wf_form（表单定义表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| form_key | VARCHAR(64) | 表单标识 | 唯一 |
| form_name | VARCHAR(128) | 表单名称 | |
| form_type | VARCHAR(32) | 表单类型 | normal/approval/dynamic |
| description | VARCHAR(500) | 描述 | |
| category | VARCHAR(64) | 分类 | |
| status | CHAR(1) | 状态 | 0-草稿，1-已发布，2-已停用 |
| current_version | INT | 当前版本号 | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**表单类型枚举**：
- `normal` - 普通表单
- `approval` - 审批表单
- `dynamic` - 动态表单

---

## AI 模块 (ai_*)

### ai_session（AI 会话表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| title | VARCHAR(255) | 会话标题 | 自动生成或用户输入 |
| user_id | BIGINT | 用户ID | 外键：sys_user |
| model | VARCHAR(64) | 使用的模型 | 如：qwen-plus |
| status | VARCHAR(32) | 会话状态 | active/archived |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**会话状态枚举**：
- `active` - 活跃
- `archived` - 已归档

---

### ai_message（AI 消息表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| session_id | BIGINT | 会话ID | 外键：ai_session |
| role | VARCHAR(32) | 角色 | user/assistant |
| content | TEXT | 消息内容 | |
| token_count | INT | token数量 | |
| sources | TEXT | 引用来源 | JSON 格式 |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**角色枚举**：
- `user` - 用户
- `assistant` - AI 助手

---

### ai_conversation（AI 对话记录表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| session_id | VARCHAR(64) | 会话ID | |
| question | TEXT | 用户问题 | |
| answer | TEXT | AI回答 | |
| model | VARCHAR(64) | 使用的模型 | |
| token_count | INT | token消耗 | |
| conversation_type | VARCHAR(32) | 对话类型 | chat/rag/stream |
| user_id | BIGINT | 用户ID | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**对话类型枚举**：
- `chat` - 普通对话
- `rag` - RAG 对话（带知识库）
- `stream` - 流式对话

---

### ai_document（AI 文档表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| title | VARCHAR(255) | 文档标题 | |
| content | LONGTEXT | 文档内容 | |
| source | VARCHAR(255) | 文档来源 | |
| doc_type | VARCHAR(64) | 文档类型 | pdf/txt/md/docx |
| vector_status | TINYINT | 向量状态 | 0-未向量化，1-已向量化 |
| user_id | BIGINT | 用户ID | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**向量状态枚举**：
- `0` - 未向量化
- `1` - 已向量化

---

### ai_embedding（AI 向量嵌入表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| document_id | BIGINT | 文档ID | 外键：ai_document |
| vector_id | VARCHAR(128) | 向量ID | 向量数据库中的ID |
| embedding_model | VARCHAR(64) | 嵌入模型 | 如：text-embedding-v2 |
| embedding | TEXT | 向量数据 | JSON数组或逗号分隔数字 |
| dimension | INT | 向量维度 | 如：1536 |
| chunk_index | INT | 分块索引 | |
| del_flag | CHAR(1) | 删除标记 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

---

## 代码生成器模块 (gen_*)

### gen_datasource_config（数据源配置表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| name | VARCHAR(50) | 数据源名称 | |
| url | VARCHAR(255) | 数据库URL | jdbc:mysql://... |
| username | VARCHAR(50) | 用户名 | |
| password | VARCHAR(100) | 密码 | 加密存储 |
| db_type | VARCHAR(10) | 数据库类型 | mysql/oracle/postgresql/sqlserver |
| status | CHAR(1) | 状态 | 0-正常，1-停用 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

**数据库类型枚举**：
- `mysql` - MySQL
- `oracle` - Oracle
- `postgresql` - PostgreSQL
- `sqlserver` - SQL Server

---

### gen_table（代码生成业务表）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| table_name | VARCHAR(100) | 表名称 | |
| table_comment | VARCHAR(255) | 表描述 | |
| class_name | VARCHAR(100) | 实体类名称 | |
| tpl_category | VARCHAR(10) | 使用的模板 | crud/tree |
| package_name | VARCHAR(100) | 生成包路径 | |
| module_name | VARCHAR(100) | 生成模块名 | |
| business_name | VARCHAR(100) | 生成业务名 | |
| function_name | VARCHAR(100) | 生成功能名 | |
| function_author | VARCHAR(50) | 生成功能作者 | |
| gen_type | CHAR(1) | 生成代码方式 | 0-zip压缩，1-自定义路径 |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

---

### gen_template（代码生成模板）

| 字段名 | 类型 | 说明 | 枚举值/备注 |
|--------|------|------|-----------|
| id | BIGINT | 主键ID | |
| template_name | VARCHAR(50) | 模板名称 | |
| template_code | VARCHAR(50) | 模板编码 | |
| template_content | LONGTEXT | 模板内容 | Velocity 模板 |
| template_desc | VARCHAR(255) | 模板描述 | |
| del_flag | CHAR(1) | 删除标志 | 0-正常，1-删除 |
| tenant_id | VARCHAR(32) | 租户ID | |
| create_by | BIGINT | 创建者 | |
| update_by | BIGINT | 更新者 | |
| create_time | DATETIME | 创建时间 | |
| update_time | DATETIME | 更新时间 | |

---

## 定时任务模块 (qrtz_*)

Quartz 定时任务框架表，详细的字段说明请参考 [Quartz 官方文档](http://www.quartz-scheduler.org/)。

**核心表**：
- `qrtz_job_details` - 任务详情
- `qrtz_triggers` - 触发器
- `qrtz_cron_triggers` - Cron 表达式触发器

---

## 附录

### 字段命名规范

- 主键：`id` (BIGINT)
- 外键：`{表名}_id` (如：`user_id`, `dept_id`)
- 状态：`status` (CHAR(1))
- 删除标志：`del_flag` (CHAR(1))
- 租户ID：`tenant_id` (VARCHAR(32))
- 创建时间：`create_time` (DATETIME)
- 更新时间：`update_time` (DATETIME)
- 创建人：`create_by` (BIGINT)
- 更新人：`update_by` (BIGINT)

### 索引命名规范

- 主键索引：`PRIMARY KEY`
- 唯一索引：`uk_{字段名}` (unique key)
- 普通索引：`idx_{字段名}` (index)
- 全文索引：`ftidx_{字段名}` (full text index)

---

**最后更新**: 2026-03-28
**维护者**: Bixi Team
