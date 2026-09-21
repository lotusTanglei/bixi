-- MySQL 8.0+: select the existing Bixi database before running this migration.
-- Adds only missing BaseEntity columns to the nine workflow/form-permission tables.
-- Existing workflow status values and all rows are retained; safe to re-run.
SET NAMES utf8mb4;

-- wf_process_definition
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `wf_process_definition` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_process_definition` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- wf_process_instance
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_process_instance` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- wf_approval_record
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_approval_record' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `wf_approval_record` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_approval_record' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_approval_record` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- wf_category
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_category' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `wf_category` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_category' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_category` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- wf_form
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_form' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_form` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- wf_form_version
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_form_version' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `wf_form_version` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_form_version' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_form_version` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- wf_form_data
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_form_data' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `wf_form_data` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'wf_form_data' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `wf_form_data` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- sys_form_permission
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `sys_form_permission` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `sys_form_permission` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

-- sys_role_form_permission
SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'create_by'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `create_by` BIGINT DEFAULT NULL COMMENT ''创建者''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'update_by'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `update_by` BIGINT DEFAULT NULL COMMENT ''更新者''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'update_time'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'del_flag'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `del_flag` CHAR(1) DEFAULT ''0'' COMMENT ''删除标记：0-正常，1-删除''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'status'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（业务）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'data_status'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `data_status` CHAR(1) DEFAULT ''0'' COMMENT ''数据状态（数据库）：0-正常''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'tenant_id'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT ''租户ID''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_role_form_permission' AND column_name = 'remark'),
    'DO 0', 'ALTER TABLE `sys_role_form_permission` ADD COLUMN `remark` VARCHAR(500) DEFAULT NULL COMMENT ''备注''');
PREPARE workflow_add_column FROM @workflow_column_sql;
EXECUTE workflow_add_column;
DEALLOCATE PREPARE workflow_add_column;

SET @workflow_column_sql = NULL;
