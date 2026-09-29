-- Scope one workflow process to each trusted source-owned business occurrence.
-- Run with workflow starts paused. Unknown legacy associations and duplicates are
-- rejected before any DDL so an operator can reconcile them explicitly.

SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_workflow_business_occurrence_20260924;
DELIMITER //
CREATE PROCEDURE bixi_migrate_workflow_business_occurrence_20260924()
BEGIN
  DECLARE table_count INT DEFAULT 0;
  DECLARE column_count INT DEFAULT 0;
  DECLARE compatible_column_count INT DEFAULT 0;
  DECLARE unsupported_count BIGINT DEFAULT 0;
  DECLARE duplicate_count BIGINT DEFAULT 0;
  DECLARE index_count INT DEFAULT 0;
  DECLARE index_non_unique_count INT DEFAULT 0;
  DECLARE index_fingerprint VARCHAR(512) DEFAULT NULL;

  SELECT COUNT(*) INTO table_count
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'wf_process_instance'
    AND table_type = 'BASE TABLE';
  IF table_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Required wf_process_instance table is missing';
  END IF;

  SELECT COUNT(*) INTO column_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'wf_process_instance'
    AND column_name = 'business_owner';

  SELECT COUNT(*) INTO compatible_column_count
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'wf_process_instance'
    AND column_name = 'business_owner'
    AND column_type = 'varchar(64)'
    AND is_nullable = 'YES'
    AND character_set_name = 'ascii'
    AND collation_name = 'ascii_bin';
  IF column_count > 0 AND compatible_column_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Existing wf_process_instance.business_owner is incompatible';
  END IF;

  SELECT COUNT(*), GROUP_CONCAT(column_name ORDER BY seq_in_index),
         SUM(CASE WHEN non_unique <> 0 THEN 1 ELSE 0 END)
    INTO index_count, index_fingerprint, index_non_unique_count
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'wf_process_instance'
    AND index_name = 'uk_wf_process_business_round';
  IF index_count > 0 AND (
      index_count <> 4
      OR index_non_unique_count <> 0
      OR index_fingerprint NOT IN (
        'tenant_id,business_table,business_id,business_round',
        'business_owner,business_table,business_id,business_round'
      )) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'uk_wf_process_business_round exists with an incompatible definition';
  END IF;

  IF column_count = 0 THEN
    SELECT COUNT(*) INTO unsupported_count
    FROM wf_process_instance
    WHERE (business_table IS NOT NULL OR business_id IS NOT NULL OR business_round IS NOT NULL)
      AND NOT (
        process_key = 'demo_leave_approval'
        AND business_table = 'demo_leave_request'
        AND business_id IS NOT NULL
        AND business_round > 0
      );

    SELECT COUNT(*) INTO duplicate_count
    FROM (
      SELECT business_table, business_id, business_round
      FROM wf_process_instance
      WHERE process_key = 'demo_leave_approval'
        AND business_table = 'demo_leave_request'
        AND business_id IS NOT NULL
        AND business_round > 0
      GROUP BY business_table, business_id, business_round
      HAVING COUNT(*) > 1
    ) duplicate_occurrences;
  ELSE
    SELECT COUNT(*) INTO unsupported_count
    FROM wf_process_instance
    WHERE (
        business_owner IS NULL
        AND (business_table IS NOT NULL OR business_id IS NOT NULL OR business_round IS NOT NULL)
        AND NOT (
          process_key = 'demo_leave_approval'
          AND business_table = 'demo_leave_request'
          AND business_id IS NOT NULL
          AND business_round > 0
        )
      ) OR (
        business_owner IS NOT NULL
        AND (business_owner = '' OR business_table IS NULL OR business_id IS NULL OR business_round IS NULL
             OR business_round <= 0)
      );

    SELECT COUNT(*) INTO duplicate_count
    FROM (
      SELECT COALESCE(business_owner, 'upms') AS resolved_owner,
             business_table, business_id, business_round
      FROM wf_process_instance
      WHERE business_owner IS NOT NULL
         OR (process_key = 'demo_leave_approval'
             AND business_table = 'demo_leave_request'
             AND business_id IS NOT NULL
             AND business_round > 0)
      GROUP BY resolved_owner, business_table, business_id, business_round
      HAVING COUNT(*) > 1
    ) duplicate_occurrences;
  END IF;

  IF unsupported_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Unsupported workflow business associations require manual owner reconciliation';
  END IF;
  IF duplicate_count > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Duplicate workflow business occurrences exist; migration refused';
  END IF;

  IF column_count = 0 THEN
    ALTER TABLE wf_process_instance
      ADD COLUMN business_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL
        COMMENT '可信业务来源owner' AFTER business_key;
  END IF;

  UPDATE wf_process_instance
  SET business_owner = 'upms'
  WHERE business_owner IS NULL
    AND process_key = 'demo_leave_approval'
    AND business_table = 'demo_leave_request'
    AND business_id IS NOT NULL
    AND business_round > 0;

  IF index_count = 0 THEN
    ALTER TABLE wf_process_instance
      ADD UNIQUE KEY uk_wf_process_business_round
        (business_owner, business_table, business_id, business_round);
  ELSEIF index_fingerprint = 'tenant_id,business_table,business_id,business_round' THEN
    ALTER TABLE wf_process_instance
      DROP INDEX uk_wf_process_business_round,
      ADD UNIQUE KEY uk_wf_process_business_round
        (business_owner, business_table, business_id, business_round);
  END IF;
END//
DELIMITER ;

CALL bixi_migrate_workflow_business_occurrence_20260924();
DROP PROCEDURE bixi_migrate_workflow_business_occurrence_20260924;
