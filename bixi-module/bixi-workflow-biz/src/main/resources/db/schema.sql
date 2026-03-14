-- 流程定义扩展表
CREATE TABLE `wf_process_definition` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `process_definition_id` VARCHAR(64) DEFAULT NULL COMMENT 'Flowable流程定义ID',
    `process_key` VARCHAR(64) NOT NULL COMMENT '流程标识',
    `process_name` VARCHAR(255) DEFAULT NULL COMMENT '流程名称',
    `category` VARCHAR(64) DEFAULT NULL COMMENT '流程分类',
    `version` INT DEFAULT 1 COMMENT '版本号',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `form_key` VARCHAR(255) DEFAULT NULL COMMENT '表单Key',
    `diagram_resource_name` VARCHAR(255) DEFAULT NULL COMMENT '流程图资源名',
    `suspension_state` INT DEFAULT 1 COMMENT '挂起状态: 1激活, 0挂起',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_process_key` (`process_key`),
    KEY `idx_process_definition_id` (`process_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程定义扩展表';

-- 流程实例扩展表
CREATE TABLE `wf_process_instance` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `process_instance_id` VARCHAR(64) NOT NULL COMMENT 'Flowable流程实例ID',
    `process_definition_id` VARCHAR(64) DEFAULT NULL COMMENT '流程定义ID',
    `process_key` VARCHAR(64) DEFAULT NULL COMMENT '流程标识',
    `business_key` VARCHAR(255) DEFAULT NULL COMMENT '业务Key',
    `business_table` VARCHAR(128) DEFAULT NULL COMMENT '业务表名',
    `business_id` BIGINT DEFAULT NULL COMMENT '业务ID',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '流程标题',
    `start_user_id` BIGINT DEFAULT NULL COMMENT '发起人ID',
    `start_user_name` VARCHAR(64) DEFAULT NULL COMMENT '发起人姓名',
    `status` VARCHAR(32) DEFAULT 'running' COMMENT '流程状态: running/completed/terminated',
    `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
    `duration` BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_business_key` (`business_key`),
    KEY `idx_start_user_id` (`start_user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程实例扩展表';

-- 审批记录表
CREATE TABLE `wf_approval_record` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `process_instance_id` VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    `task_id` VARCHAR(64) DEFAULT NULL COMMENT '任务ID',
    `task_name` VARCHAR(255) DEFAULT NULL COMMENT '任务名称',
    `task_key` VARCHAR(64) DEFAULT NULL COMMENT '任务Key',
    `approval_type` VARCHAR(32) DEFAULT NULL COMMENT '审批类型: approve/reject/transfer/delegate',
    `approval_user_id` BIGINT DEFAULT NULL COMMENT '审批人ID',
    `approval_user_name` VARCHAR(64) DEFAULT NULL COMMENT '审批人姓名',
    `approval_comment` VARCHAR(1000) DEFAULT NULL COMMENT '审批意见',
    `approval_time` DATETIME DEFAULT NULL COMMENT '审批时间',
    `delegate_user_id` BIGINT DEFAULT NULL COMMENT '被委托人ID',
    `delegate_user_name` VARCHAR(64) DEFAULT NULL COMMENT '被委托人姓名',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_approval_user_id` (`approval_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批记录表';

-- 流程分类表
CREATE TABLE `wf_category` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `category_name` VARCHAR(128) NOT NULL COMMENT '分类名称',
    `category_code` VARCHAR(64) NOT NULL COMMENT '分类编码',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID',
    `sn` INT DEFAULT 0 COMMENT '排序号',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_category_code` (`category_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程分类表';
