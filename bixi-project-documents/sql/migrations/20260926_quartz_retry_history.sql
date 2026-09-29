-- Persist bounded retry configuration and queryable Quartz execution evidence.
-- Run with scheduler nodes and task-management writes stopped.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_quartz_retry_history_20260926;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_quartz_retry_history_20260926()
BEGIN
    DECLARE object_count BIGINT DEFAULT 0;
    DECLARE definition_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;
    DECLARE index_columns VARCHAR(255) DEFAULT NULL;

    SELECT COUNT(*) INTO object_count FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_job table is missing';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_job_record table is missing';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND column_name = 'retry_count';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND column_name = 'retry_count'
      AND column_type = 'int' AND is_nullable = 'NO' AND column_default = '0';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job.retry_count definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND column_name = 'retry_interval_seconds';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND column_name = 'retry_interval_seconds'
      AND column_type = 'int' AND is_nullable = 'NO' AND column_default = '5';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job.retry_interval_seconds definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'execution_id';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'execution_id'
      AND column_type = 'varchar(128)' AND is_nullable = 'YES';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job_record.execution_id definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'attempt';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'attempt'
      AND column_type = 'int' AND is_nullable = 'NO' AND column_default = '1';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job_record.attempt definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'max_attempts';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'max_attempts'
      AND column_type = 'int' AND is_nullable = 'NO' AND column_default = '1';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job_record.max_attempts definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'trigger_type';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'trigger_type'
      AND column_type = 'varchar(16)' AND is_nullable = 'NO' AND column_default = 'LEGACY';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job_record.trigger_type definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'recovered';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'recovered'
      AND column_type = 'tinyint(1)' AND is_nullable = 'NO' AND column_default = '0';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_job_record.recovered definition';
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record'
      AND index_name = 'idx_job_record_job_created';
    IF index_count > 0 THEN
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'sys_job_record'
          AND index_name = 'idx_job_record_job_created';
        IF index_columns <> 'job_id,create_time,id' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting idx_job_record_job_created definition';
        END IF;
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record'
      AND index_name = 'idx_job_record_execution';
    IF index_count > 0 THEN
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'sys_job_record'
          AND index_name = 'idx_job_record_execution';
        IF index_columns <> 'execution_id,attempt' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting idx_job_record_execution definition';
        END IF;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND column_name = 'retry_count';
    IF object_count = 0 THEN
        ALTER TABLE sys_job ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER misfire_policy;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job' AND column_name = 'retry_interval_seconds';
    IF object_count = 0 THEN
        ALTER TABLE sys_job ADD COLUMN retry_interval_seconds INT NOT NULL DEFAULT 5 AFTER retry_count;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'execution_id';
    IF object_count = 0 THEN
        ALTER TABLE sys_job_record ADD COLUMN execution_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER job_id;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'attempt';
    IF object_count = 0 THEN
        ALTER TABLE sys_job_record ADD COLUMN attempt INT NOT NULL DEFAULT 1 AFTER execution_id;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'max_attempts';
    IF object_count = 0 THEN
        ALTER TABLE sys_job_record ADD COLUMN max_attempts INT NOT NULL DEFAULT 1 AFTER attempt;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'trigger_type';
    IF object_count = 0 THEN
        ALTER TABLE sys_job_record ADD COLUMN trigger_type VARCHAR(16) NOT NULL DEFAULT 'LEGACY' AFTER max_attempts;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record' AND column_name = 'recovered';
    IF object_count = 0 THEN
        ALTER TABLE sys_job_record ADD COLUMN recovered TINYINT(1) NOT NULL DEFAULT 0 AFTER trigger_type;
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record'
      AND index_name = 'idx_job_record_job_created';
    IF index_count = 0 THEN
        CREATE INDEX idx_job_record_job_created ON sys_job_record(job_id, create_time, id);
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_job_record'
      AND index_name = 'idx_job_record_execution';
    IF index_count = 0 THEN
        CREATE INDEX idx_job_record_execution ON sys_job_record(execution_id, attempt);
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_quartz_retry_history_20260926();
DROP PROCEDURE bixi_migrate_quartz_retry_history_20260926;
