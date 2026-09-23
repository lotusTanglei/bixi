-- Additive Stage 2E migration for existing installations.
-- The table is deliberately owner-local and keeps CANCELED tombstones for replay safety.
-- CREATE TABLE IF NOT EXISTS makes a second execution a no-op.
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
