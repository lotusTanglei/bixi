-- Additive, repeatable START-only migration. Run while workflow writes are stopped.
-- Existing duplicate engine IDs stop before schema changes; no data is deleted or deduplicated.
-- Historical rows retain NULL start_request_id. Business-round uniqueness is deferred to 2C.
DROP PROCEDURE IF EXISTS migrate_workflow_stage2_start;
DELIMITER $$
CREATE PROCEDURE migrate_workflow_stage2_start()
BEGIN
    IF EXISTS (SELECT process_instance_id FROM wf_process_instance GROUP BY process_instance_id HAVING COUNT(*) > 1) THEN
        SELECT process_instance_id, COUNT(*) AS duplicate_count FROM wf_process_instance
            GROUP BY process_instance_id HAVING COUNT(*) > 1;
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate workflow process_instance_id; reconcile explicitly before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
            AND table_name = 'wf_process_instance' AND index_name = 'uk_wf_process_instance_id')
        AND NOT EXISTS (SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE()
            AND table_name = 'wf_process_instance' AND index_name = 'uk_wf_process_instance_id'
            GROUP BY index_name HAVING COUNT(*) = 1 AND MAX(non_unique) = 0
                AND MAX(column_name) = 'process_instance_id' AND MAX(sub_part) IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid uk_wf_process_instance_id; reconcile index definition before migration';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE()
            AND table_name = 'wf_process_instance' AND column_name = 'start_request_id') THEN
        ALTER TABLE wf_process_instance ADD COLUMN start_request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
            AND table_name = 'wf_process_instance' AND index_name = 'uk_wf_process_instance_id') THEN
        ALTER TABLE wf_process_instance ADD UNIQUE KEY uk_wf_process_instance_id(process_instance_id);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
            AND table_name = 'wf_process_instance' AND index_name = 'idx_process_instance_id') THEN
        ALTER TABLE wf_process_instance DROP INDEX idx_process_instance_id;
    END IF;

    -- Workflow START commands; a successful row commits with its engine transaction.
    CREATE TABLE IF NOT EXISTS `wf_command` (
        `id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        `tenant_scope` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'default',
        `actor_id` BIGINT NOT NULL,
        `actor_name` VARCHAR(64) NOT NULL,
        `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        `source_owner` VARCHAR(32) NOT NULL,
        `operation` VARCHAR(32) NOT NULL,
        `resource_id` VARCHAR(255) DEFAULT NULL,
        `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        `hash_version` INT NOT NULL DEFAULT 1,
        `status` VARCHAR(16) NOT NULL,
        `response_json` LONGTEXT DEFAULT NULL,
        `result_code` VARCHAR(64) DEFAULT NULL,
        `process_instance_id` VARCHAR(64) DEFAULT NULL,
        `terminal_task_id` VARCHAR(64) DEFAULT NULL,
        `created_at` DATETIME(6) NOT NULL,
        `completed_at` DATETIME(6) DEFAULT NULL,
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_wf_command_request` (`tenant_scope`, `actor_id`, `request_id`),
        UNIQUE KEY `uk_wf_command_terminal_task` (`terminal_task_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流请求幂等结果';

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
            AND table_name = 'wf_command' AND index_name = 'idx_wf_command_actor_created') THEN
        CREATE INDEX idx_wf_command_actor_created ON wf_command(tenant_scope, actor_id, created_at);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
            AND table_name = 'wf_command' AND index_name = 'idx_wf_command_process') THEN
        CREATE INDEX idx_wf_command_process ON wf_command(process_instance_id);
    END IF;
END$$
DELIMITER ;
CALL migrate_workflow_stage2_start();
DROP PROCEDURE migrate_workflow_stage2_start;
