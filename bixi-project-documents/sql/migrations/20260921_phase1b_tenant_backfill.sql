-- Phase 1B tenant backfill.
-- Run after 01_init_all_tables.sql and before application traffic is enabled.
-- Existing rows without an owner are assigned to the default tenant (1).

INSERT INTO sys_tenant (id, name, code, status, max_user_count, del_flag)
SELECT 1, '默认租户', 'default', '0', -1, '0'
WHERE NOT EXISTS (SELECT 1 FROM sys_tenant WHERE id = 1);

UPDATE biz_demo_task SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE demo_leave_request SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_dept SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_dict SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_dict_item SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_file SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_log SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_menu SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_oauth_client_details SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_post SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_public_param SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_role SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_user SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_notice SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_user_notice SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE ai_session SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE ai_message SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE ai_conversation SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE ai_document SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE ai_embedding SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_process_definition SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_process_instance SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_approval_record SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_category SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_form SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_form_version SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE wf_form_data SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_form_permission SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_role_form_permission SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE gen_datasource_config SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE gen_field_type SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE gen_group SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE gen_table SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE gen_table_column SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE gen_template SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_job SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_job_record SET tenant_id = 1 WHERE tenant_id IS NULL;
UPDATE sys_sensitive_word SET tenant_id = 1 WHERE tenant_id IS NULL;

-- Department closure rows are global relations and are intentionally not
-- tenant-filtered; rebuild them from the current department hierarchy.
INSERT IGNORE INTO sys_dept_relation (ancestor, descendant)
SELECT id, id FROM sys_dept;
