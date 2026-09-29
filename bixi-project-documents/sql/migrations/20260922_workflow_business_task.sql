-- Additive Stage 2E migration for existing installations.
-- The table is deliberately owner-local and keeps CANCELED tombstones for replay safety.
-- CREATE TABLE IF NOT EXISTS makes a second execution a no-op.
CREATE TABLE IF NOT EXISTS `wf_business_task` (
  `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定外部操作ID',
  `process_instance_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '流程实例ID',
  `execution_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '首次请求执行ID',
  `activity_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '首次请求活动ID',
  `activity_occurrence` int NOT NULL COMMENT '活动发生序号',
  `business_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务owner',
  `business_table` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务表',
  `business_id` bigint NOT NULL COMMENT '业务ID',
  `business_round` int NOT NULL COMMENT '业务轮次',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求摘要',
  `tenant_scope` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租户范围',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'WAITING/SUCCEEDED/FAILED/TIMED_OUT/CANCELED/COMPENSATING/COMPENSATED',
  `deadline` datetime(6) NOT NULL COMMENT '结果截止时间',
  `result_event_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '最近结果事件ID',
  `compensation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定补偿ID',
  `last_error` varchar(128) DEFAULT NULL COMMENT '脱敏错误码',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_wf_business_task_occurrence` (`process_instance_id`,`activity_id`,`activity_occurrence`),
  UNIQUE KEY `uk_wf_business_task_compensation` (`compensation_id`),
  KEY `idx_wf_business_task_status_deadline` (`status`,`deadline`),
  CONSTRAINT `chk_wf_business_task_status` CHECK (`status` IN
    ('WAITING','SUCCEEDED','FAILED','TIMED_OUT','CANCELED','COMPENSATING','COMPENSATED')),
  CONSTRAINT `chk_wf_business_task_round` CHECK (`business_round` > 0),
  CONSTRAINT `chk_wf_business_task_occurrence` CHECK (`activity_occurrence` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Workflow外部业务任务状态';

CREATE TABLE IF NOT EXISTS `demo_leave_booking` (
  `operation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '自动任务操作ID',
  `leave_id` bigint NOT NULL COMMENT '请假ID',
  `round` int NOT NULL COMMENT '业务申请轮次',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求摘要',
  `booking_state` varchar(16) NOT NULL COMMENT '登记状态：BOOKED/CANCELED',
  `booking_reference` varchar(128) DEFAULT NULL COMMENT '登记引用号',
  `compensation_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '稳定补偿ID',
  `tenant_id` bigint NOT NULL DEFAULT '1' COMMENT '租户ID',
  `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_leave_booking_round` (`tenant_id`,`leave_id`,`round`),
  UNIQUE KEY `uk_leave_booking_compensation` (`compensation_id`),
  KEY `idx_leave_booking_state_updated` (`tenant_id`,`booking_state`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='请假审批后的幂等登记与补偿记录';
