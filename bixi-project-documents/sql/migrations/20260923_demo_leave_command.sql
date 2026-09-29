-- Stage 2 trusted leave submission command. Safe to run before enabling reliable workflow submission.
-- An existing table must match the canonical contract exactly; incompatible data is never rewritten.

DROP PROCEDURE IF EXISTS bixi_migrate_demo_leave_command_20260923;
DELIMITER //
CREATE PROCEDURE bixi_migrate_demo_leave_command_20260923()
BEGIN
  DECLARE table_count INT DEFAULT 0;
  DECLARE column_fingerprint LONGTEXT;
  DECLARE index_fingerprint LONGTEXT;
  DECLARE check_fingerprint LONGTEXT;
  DECLARE table_engine VARCHAR(64);
  DECLARE table_collation VARCHAR(64);

  SELECT COUNT(*) INTO table_count
  FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'demo_leave_command';

  IF table_count = 0 THEN
    CREATE TABLE `demo_leave_command` (
      `command_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      `tenant_scope` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      `actor_id` bigint NOT NULL,
      `actor_name` varchar(64) NOT NULL,
      `client_request_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      `operation` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      `leave_id` bigint NOT NULL,
      `round` int NOT NULL,
      `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      `hash_version` int NOT NULL DEFAULT 1,
      `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
      `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
      `process_instance_id` varchar(64) DEFAULT NULL,
      `error_code` varchar(64) DEFAULT NULL,
      `response_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
      `created_at` datetime(6) NOT NULL,
      `completed_at` datetime(6) DEFAULT NULL,
      PRIMARY KEY (`command_id`),
      UNIQUE KEY `uk_demo_leave_command_request` (`tenant_scope`, `actor_id`, `client_request_id`),
      UNIQUE KEY `uk_demo_leave_command_business` (`leave_id`, `round`, `operation`),
      CONSTRAINT `chk_demo_leave_command_status` CHECK (`status` IN ('ACCEPTED', 'STARTED', 'REJECTED')),
      CONSTRAINT `chk_demo_leave_command_round` CHECK (`round` > 0),
      CONSTRAINT `chk_demo_leave_command_hash_version` CHECK (`hash_version` = 1)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='请假提交幂等命令';
  ELSE
    SELECT engine, table_collation INTO table_engine, table_collation
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'demo_leave_command';

    SELECT GROUP_CONCAT(CONCAT_WS('|', ordinal_position, column_name, column_type, is_nullable,
               COALESCE(column_default, '<NULL>'), COALESCE(collation_name, '<NULL>'))
               ORDER BY ordinal_position SEPARATOR ';')
      INTO column_fingerprint
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'demo_leave_command';

    SELECT GROUP_CONCAT(CONCAT(index_name, '|', non_unique, '|', column_names)
               ORDER BY index_name SEPARATOR ';')
      INTO index_fingerprint
    FROM (
      SELECT index_name, non_unique,
             GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS column_names
      FROM information_schema.statistics
      WHERE table_schema = DATABASE() AND table_name = 'demo_leave_command'
      GROUP BY index_name, non_unique
    ) AS command_indexes;

    SELECT GROUP_CONCAT(CONCAT(constraint_name, '|', constraint_type)
               ORDER BY constraint_name SEPARATOR ';')
      INTO check_fingerprint
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'demo_leave_command'
      AND constraint_type = 'CHECK';

    IF table_engine <> 'InnoDB' OR table_collation <> 'utf8mb4_bin'
       OR column_fingerprint <> CONCAT(
         '1|command_id|char(36)|NO|<NULL>|ascii_bin;',
         '2|tenant_scope|varchar(32)|NO|<NULL>|ascii_bin;',
         '3|actor_id|bigint|NO|<NULL>|<NULL>;',
         '4|actor_name|varchar(64)|NO|<NULL>|utf8mb4_bin;',
         '5|client_request_id|char(36)|NO|<NULL>|ascii_bin;',
         '6|operation|varchar(32)|NO|<NULL>|ascii_bin;',
         '7|leave_id|bigint|NO|<NULL>|<NULL>;',
         '8|round|int|NO|<NULL>|<NULL>;',
         '9|request_hash|char(64)|NO|<NULL>|ascii_bin;',
         '10|hash_version|int|NO|1|<NULL>;',
         '11|payload_json|longtext|NO|<NULL>|utf8mb4_bin;',
         '12|status|varchar(16)|NO|<NULL>|ascii_bin;',
         '13|process_instance_id|varchar(64)|YES|<NULL>|utf8mb4_bin;',
         '14|error_code|varchar(64)|YES|<NULL>|utf8mb4_bin;',
         '15|response_json|longtext|YES|<NULL>|utf8mb4_bin;',
         '16|created_at|datetime(6)|NO|<NULL>|<NULL>;',
         '17|completed_at|datetime(6)|YES|<NULL>|<NULL>')
       OR index_fingerprint <> CONCAT(
         'PRIMARY|0|command_id;',
         'uk_demo_leave_command_business|0|leave_id,round,operation;',
         'uk_demo_leave_command_request|0|tenant_scope,actor_id,client_request_id')
       OR check_fingerprint <> CONCAT(
         'chk_demo_leave_command_hash_version|CHECK;',
         'chk_demo_leave_command_round|CHECK;',
         'chk_demo_leave_command_status|CHECK') THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'demo_leave_command exists with an incompatible schema; migration refused';
    END IF;
  END IF;
END//
DELIMITER ;

CALL bixi_migrate_demo_leave_command_20260923();
DROP PROCEDURE bixi_migrate_demo_leave_command_20260923;
