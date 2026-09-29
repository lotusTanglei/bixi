-- Freeze published form versions on Flowable definitions and process instances.
-- Existing definitions and instances remain explicitly unbound (NULL); the application
-- does not guess historical form versions. Stop workflow writes while this runs.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_workflow_form_binding_20260924;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_workflow_form_binding_20260924()
BEGIN
    DECLARE duplicate_count BIGINT DEFAULT 0;

    IF (SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name IN
                ('wf_form', 'wf_form_version', 'wf_form_data', 'wf_process_definition',
                 'wf_process_instance', 'sys_form_permission')) <> 6 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing workflow form tables; apply the base workflow schema first';
    END IF;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT form_id, version FROM wf_form_version
        GROUP BY form_id, version HAVING COUNT(*) > 1
    ) duplicate_versions;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate workflow form versions require manual resolution';
    END IF;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT process_definition_id FROM wf_process_definition
        WHERE process_definition_id IS NOT NULL
        GROUP BY process_definition_id HAVING COUNT(*) > 1
    ) duplicate_definitions;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate workflow process definition IDs require manual resolution';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition'
                AND column_name = 'form_version_id') THEN
        ALTER TABLE wf_process_definition
            ADD COLUMN form_version_id BIGINT NULL COMMENT '发布时固定的表单版本ID'
            AFTER form_key;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance'
                AND column_name = 'form_id') THEN
        ALTER TABLE wf_process_instance
            ADD COLUMN form_id BIGINT NULL COMMENT '发起时固定的表单ID'
            AFTER process_key;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance'
                AND column_name = 'form_version_id') THEN
        ALTER TABLE wf_process_instance
            ADD COLUMN form_version_id BIGINT NULL COMMENT '发起时固定的表单版本ID'
            AFTER form_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission'
                AND column_name = 'form_version_id') THEN
        ALTER TABLE sys_form_permission
            ADD COLUMN form_version_id BIGINT NULL COMMENT '表单版本ID，为空表示全部版本'
            AFTER form_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission'
                AND column_name = 'process_definition_id') THEN
        ALTER TABLE sys_form_permission
            ADD COLUMN process_definition_id VARCHAR(64) NULL COMMENT 'Flowable流程定义ID，为空表示全部定义'
            AFTER form_version_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission'
                AND column_name = 'task_definition_key') THEN
        ALTER TABLE sys_form_permission
            ADD COLUMN task_definition_key VARCHAR(64) NULL COMMENT '任务定义Key，__start__ 表示发起节点，为空表示全部节点'
            AFTER process_definition_id;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition'
                AND column_name = 'form_version_id' AND (data_type <> 'bigint' OR is_nullable <> 'YES'))
        OR EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance'
                AND column_name IN ('form_id', 'form_version_id')
                AND (data_type <> 'bigint' OR is_nullable <> 'YES'))
        OR EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission'
                AND column_name = 'form_version_id' AND (data_type <> 'bigint' OR is_nullable <> 'YES'))
        OR EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission'
                AND column_name IN ('process_definition_id', 'task_definition_key')
                AND (data_type <> 'varchar' OR character_maximum_length <> 64 OR is_nullable <> 'YES')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Workflow form binding columns have incompatible definitions';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'wf_form_version'
                AND index_name = 'uk_wf_form_version'
            GROUP BY index_name
            HAVING MIN(non_unique) <> 0 OR COUNT(*) <> 2
                OR GROUP_CONCAT(column_name ORDER BY seq_in_index) <> 'form_id,version') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid uk_wf_form_version index';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'wf_form_version'
                AND index_name = 'uk_wf_form_version') THEN
        ALTER TABLE wf_form_version
            ADD UNIQUE INDEX uk_wf_form_version (form_id, version);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition'
                AND index_name = 'uk_wf_process_definition_id'
            GROUP BY index_name
            HAVING MIN(non_unique) <> 0 OR COUNT(*) <> 1
                OR MAX(column_name) <> 'process_definition_id') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid uk_wf_process_definition_id index';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition'
                AND index_name = 'uk_wf_process_definition_id') THEN
        ALTER TABLE wf_process_definition
            ADD UNIQUE INDEX uk_wf_process_definition_id (process_definition_id);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_definition'
                AND index_name = 'idx_wf_definition_form_version') THEN
        ALTER TABLE wf_process_definition
            ADD INDEX idx_wf_definition_form_version (form_version_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance'
                AND index_name = 'idx_wf_instance_form_version') THEN
        ALTER TABLE wf_process_instance
            ADD INDEX idx_wf_instance_form_version (form_version_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'sys_form_permission'
                AND index_name = 'idx_form_permission_scope') THEN
        ALTER TABLE sys_form_permission
            ADD INDEX idx_form_permission_scope
                (form_id, form_version_id, process_definition_id, task_definition_key);
    END IF;

    IF EXISTS (SELECT 1 FROM wf_process_definition d LEFT JOIN wf_form_version v ON v.id = d.form_version_id
            WHERE d.form_version_id IS NOT NULL AND v.id IS NULL)
        OR EXISTS (SELECT 1 FROM wf_process_instance i LEFT JOIN wf_form f ON f.id = i.form_id
            WHERE i.form_id IS NOT NULL AND f.id IS NULL)
        OR EXISTS (SELECT 1 FROM wf_process_instance i LEFT JOIN wf_form_version v ON v.id = i.form_version_id
            WHERE i.form_version_id IS NOT NULL AND v.id IS NULL)
        OR EXISTS (SELECT 1 FROM wf_form_data d LEFT JOIN wf_form_version v ON v.id = d.form_version_id
            WHERE d.form_version_id IS NOT NULL AND v.id IS NULL)
        OR EXISTS (SELECT 1 FROM sys_form_permission p LEFT JOIN wf_form_version v ON v.id = p.form_version_id
            WHERE p.form_version_id IS NOT NULL AND v.id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan workflow form version references require manual resolution';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_wf_definition_form_version') THEN
        ALTER TABLE wf_process_definition ADD CONSTRAINT fk_wf_definition_form_version
            FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
            ON DELETE RESTRICT ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_wf_instance_form') THEN
        ALTER TABLE wf_process_instance ADD CONSTRAINT fk_wf_instance_form
            FOREIGN KEY (form_id) REFERENCES wf_form(id)
            ON DELETE RESTRICT ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_wf_instance_form_version') THEN
        ALTER TABLE wf_process_instance ADD CONSTRAINT fk_wf_instance_form_version
            FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
            ON DELETE RESTRICT ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_form_data_form_version') THEN
        ALTER TABLE wf_form_data ADD CONSTRAINT fk_form_data_form_version
            FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
            ON DELETE RESTRICT ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.referential_constraints
            WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_form_permission_version') THEN
        ALTER TABLE sys_form_permission ADD CONSTRAINT fk_form_permission_version
            FOREIGN KEY (form_version_id) REFERENCES wf_form_version(id)
            ON DELETE RESTRICT ON UPDATE CASCADE;
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_workflow_form_binding_20260924();
DROP PROCEDURE bixi_migrate_workflow_form_binding_20260924;
