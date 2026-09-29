-- Add stable request identity and replayable results to both owner-local recovery audit tables.
DELIMITER $$
DROP PROCEDURE IF EXISTS bixi_migrate_workflow_recovery_request_20260926$$
CREATE PROCEDURE bixi_migrate_workflow_recovery_request_20260926()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'request_id') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER reason;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'expected_status') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN expected_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER request_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'resource_type') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN resource_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER expected_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'resource_id') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN resource_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER resource_type;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'previous_status') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN previous_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER resource_id;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'current_status') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN current_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER previous_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'outcome') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN outcome VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER current_status;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'detail') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN detail VARCHAR(256) NULL AFTER outcome;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit' AND column_name = 'completed_at') THEN
    ALTER TABLE wf_recovery_audit
      ADD COLUMN completed_at DATETIME(6) NULL AFTER detail;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'wf_recovery_audit'
        AND index_name = 'uk_wf_recovery_request') THEN
    ALTER TABLE wf_recovery_audit
      ADD UNIQUE KEY uk_wf_recovery_request (owner, action, request_id);
  END IF;
END$$
CALL bixi_migrate_workflow_recovery_request_20260926()$$
DROP PROCEDURE bixi_migrate_workflow_recovery_request_20260926$$
DELIMITER ;
