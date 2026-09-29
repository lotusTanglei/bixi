-- =====================================================
-- 数据库约束优化脚本
-- 版本: 1.0.0
-- 日期: 2026-03-28
-- 说明: 添加唯一约束和外键约束（可选执行）
-- =====================================================

-- 注意：执行前请确保数据符合约束条件，否则会失败

-- =====================================================
-- 1. 唯一约束（推荐执行）
-- =====================================================

-- 用户表唯一约束
ALTER TABLE sys_user ADD CONSTRAINT uk_user_tenant_username UNIQUE (tenant_id, username);
ALTER TABLE sys_user ADD CONSTRAINT uk_user_tenant_phone UNIQUE (tenant_id, phone);
ALTER TABLE sys_user ADD CONSTRAINT uk_user_tenant_email UNIQUE (tenant_id, email);

-- 字典表唯一约束
ALTER TABLE sys_dict ADD CONSTRAINT uk_dict_tenant_type UNIQUE (tenant_id, type);

-- 角色表唯一约束
ALTER TABLE sys_role ADD CONSTRAINT uk_role_tenant_code UNIQUE (tenant_id, code);

-- 菜单表唯一约束
ALTER TABLE sys_menu ADD CONSTRAINT uk_menu_tenant_perm UNIQUE (tenant_id, permission);

-- 部门表唯一约束
ALTER TABLE sys_dept ADD CONSTRAINT uk_dept_tenant_code UNIQUE (tenant_id, code);

-- 工作流分类唯一约束
ALTER TABLE wf_category ADD CONSTRAINT uk_category_code UNIQUE (category_code);

-- 表单定义唯一约束
-- wf_form.uk_form_key is already defined in 01_init_all_tables.sql.

-- =====================================================
-- 2. 外键约束（可选执行）
-- =====================================================
-- 注意：外键约束会影响性能，请根据实际需求决定是否启用

-- sys_menu and sys_dept use sentinel parent_id values for roots (`-1` and `0`).
-- Self-referential foreign keys are intentionally not added here because they
-- would reject existing root records in 04_init_data.sql.

-- 用户-部门关联
ALTER TABLE sys_user
ADD CONSTRAINT fk_user_dept
FOREIGN KEY (dept_id) REFERENCES sys_dept(id)
ON DELETE SET NULL
ON UPDATE CASCADE;

-- 用户-角色关联表
ALTER TABLE sys_user_role
ADD CONSTRAINT fk_user_role_user
FOREIGN KEY (user_id) REFERENCES sys_user(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

ALTER TABLE sys_user_role
ADD CONSTRAINT fk_user_role_role
FOREIGN KEY (role_id) REFERENCES sys_role(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- 角色-菜单关联表
ALTER TABLE sys_role_menu
ADD CONSTRAINT fk_role_menu_role
FOREIGN KEY (role_id) REFERENCES sys_role(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

ALTER TABLE sys_role_menu
ADD CONSTRAINT fk_role_menu_menu
FOREIGN KEY (menu_id) REFERENCES sys_menu(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- 字典项-字典关联
ALTER TABLE sys_dict_item
ADD CONSTRAINT fk_dict_item_dict
FOREIGN KEY (dict_id) REFERENCES sys_dict(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- AI 消息-会话关联
ALTER TABLE ai_message
ADD CONSTRAINT fk_message_session
FOREIGN KEY (session_id) REFERENCES ai_session(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- AI 向量嵌入-文档关联
ALTER TABLE ai_embedding
ADD CONSTRAINT fk_embedding_document
FOREIGN KEY (document_id) REFERENCES ai_document(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- 表单数据-表单关联
ALTER TABLE wf_form_data
ADD CONSTRAINT fk_form_data_form
FOREIGN KEY (form_id) REFERENCES wf_form(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- 表单版本-表单关联
ALTER TABLE wf_form_version
ADD CONSTRAINT fk_form_version_form
FOREIGN KEY (form_id) REFERENCES wf_form(id)
ON DELETE CASCADE
ON UPDATE CASCADE;

-- 流程定义-已发布表单版本关联
ALTER TABLE wf_process_definition
ADD CONSTRAINT fk_wf_definition_form_version
FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- 流程实例-固定表单及版本关联
ALTER TABLE wf_process_instance
ADD CONSTRAINT fk_wf_instance_form
FOREIGN KEY (form_id) REFERENCES wf_form(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

ALTER TABLE wf_process_instance
ADD CONSTRAINT fk_wf_instance_form_version
FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- 表单快照-固定版本关联
ALTER TABLE wf_form_data
ADD CONSTRAINT fk_form_data_form_version
FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- 字段权限-可选版本作用域
ALTER TABLE sys_form_permission
ADD CONSTRAINT fk_form_permission_version
FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
ON DELETE RESTRICT
ON UPDATE CASCADE;

-- =====================================================
-- 3. 检查约束（可选）
-- =====================================================

-- 用户状态检查
ALTER TABLE sys_user
ADD CONSTRAINT chk_user_lock_flag
CHECK (lock_flag IN ('0', '9'));

ALTER TABLE sys_user
ADD CONSTRAINT chk_user_status
CHECK (status IN ('0', '1'));

-- 菜单可见性检查
ALTER TABLE sys_menu
ADD CONSTRAINT chk_menu_visible
CHECK (visible IN ('0', '1'));

-- 完成后，可以使用以下命令检查约束
-- SELECT * FROM information_schema.TABLE_CONSTRAINTS
-- WHERE TABLE_SCHEMA = 'bixi';
