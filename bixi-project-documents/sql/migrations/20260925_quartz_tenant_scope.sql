-- Scope Quartz task identities to their persisted tenant.
-- Run after 20260921_phase1b_tenant_backfill.sql with scheduler writes paused.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_quartz_tenant_scope_20260925;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_quartz_tenant_scope_20260925()
BEGIN
    DECLARE invalid_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;
    DECLARE index_columns VARCHAR(255) DEFAULT NULL;

    SELECT COUNT(*) INTO invalid_count FROM sys_job WHERE tenant_id IS NULL OR tenant_id <= 0;
    IF invalid_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quartz jobs require a positive tenant_id before migration';
    END IF;

    SELECT COUNT(*) INTO invalid_count FROM (
        SELECT tenant_id, name, `group` FROM sys_job
        GROUP BY tenant_id, name, `group` HAVING COUNT(*) > 1
    ) duplicates;
    IF invalid_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate tenant-local Quartz task names require manual resolution';
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job'
      AND index_name = 'uk_job_tenant_name_group';
    IF index_count > 0 THEN
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'sys_job'
          AND index_name = 'uk_job_tenant_name_group';
        IF index_columns <> 'tenant_id,name,group' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting uk_job_tenant_name_group index definition';
        END IF;
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job'
      AND index_name = 'job_name_group_idx';
    IF index_count > 0 THEN
        ALTER TABLE sys_job DROP INDEX job_name_group_idx;
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job'
      AND index_name = 'uk_job_tenant_name_group';
    IF index_count = 0 THEN
        ALTER TABLE sys_job
            ADD CONSTRAINT uk_job_tenant_name_group UNIQUE (tenant_id, name, `group`);
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_quartz_tenant_scope_20260925();
DROP PROCEDURE bixi_migrate_quartz_tenant_scope_20260925;
