-- Preserve the immutable request digest needed to correlate terminal workflow events.
SET @workflow_hash_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'wf_process_instance'
             AND column_name = 'start_request_hash'),
    'SELECT 1',
    'ALTER TABLE wf_process_instance ADD COLUMN start_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER start_request_id'
);
PREPARE workflow_hash_statement FROM @workflow_hash_ddl;
EXECUTE workflow_hash_statement;
DEALLOCATE PREPARE workflow_hash_statement;
