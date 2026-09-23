-- Additive migration for existing installations; fresh databases use canonical init scripts.
CREATE TABLE IF NOT EXISTS `demo_leave_request` (
  `id` bigint NOT NULL COMMENT '请假ID',
  `applicant_id` bigint NOT NULL COMMENT '申请人',
  `approver_id` bigint NOT NULL COMMENT '审批人',
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `reason` varchar(1000) NOT NULL,
  `leave_status` varchar(16) NOT NULL DEFAULT 'DRAFT',
  `business_key` varchar(255) NOT NULL,
  `round` int NOT NULL DEFAULT 1,
  `process_instance_id` varchar(64) DEFAULT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `ended_at` datetime DEFAULT NULL,
  `create_by` bigint DEFAULT NULL,
  `update_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `del_flag` char(1) DEFAULT '0',
  `status` char(1) DEFAULT '0',
  `data_status` char(1) DEFAULT '0',
  `tenant_id` bigint DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  KEY `idx_leave_applicant_created` (`applicant_id`, `del_flag`, `create_time`),
  KEY `idx_leave_applicant_status` (`applicant_id`, `leave_status`, `del_flag`),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_leave_business_key` (`business_key`),
  UNIQUE KEY `uk_leave_process_instance` (`process_instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工作流请假示例';

SET @leave_command_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'demo_leave_request'
             AND column_name = 'start_command_id'),
    'SELECT 1',
    'ALTER TABLE demo_leave_request ADD COLUMN start_command_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER process_instance_id'
);
PREPARE leave_command_statement FROM @leave_command_ddl;
EXECUTE leave_command_statement;
DEALLOCATE PREPARE leave_command_statement;

SET @leave_hash_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'demo_leave_request'
             AND column_name = 'start_request_hash'),
    'SELECT 1',
    'ALTER TABLE demo_leave_request ADD COLUMN start_request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL AFTER start_command_id'
);
PREPARE leave_hash_statement FROM @leave_hash_ddl;
EXECUTE leave_hash_statement;
DEALLOCATE PREPARE leave_hash_statement;
