CREATE TABLE `wf_form` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `form_key` VARCHAR(64) NOT NULL COMMENT '表单标识',
    `form_name` VARCHAR(128) NOT NULL COMMENT '表单名称',
    `form_type` VARCHAR(32) DEFAULT 'normal' COMMENT '表单类型：normal/approval/dynamic',
    `description` VARCHAR(500) COMMENT '描述',
    `category` VARCHAR(64) COMMENT '分类',
    `status` CHAR(1) DEFAULT '0' COMMENT '状态：0-草稿，1-已发布，2-已停用',
    `current_version` INT DEFAULT 1 COMMENT '当前版本号',
    `create_by` BIGINT COMMENT '创建者',
    `update_by` BIGINT COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记',
    `tenant_id` VARCHAR(32) COMMENT '租户ID',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_form_key` (`form_key`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单定义表';

CREATE TABLE `wf_form_version` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `form_id` BIGINT NOT NULL COMMENT '表单ID',
    `version` INT NOT NULL COMMENT '版本号',
    `schema_json` LONGTEXT COMMENT '表单JSON Schema',
    `change_log` VARCHAR(500) COMMENT '变更日志',
    `is_active` TINYINT DEFAULT 0 COMMENT '是否激活版本：0-否，1-是',
    `create_by` BIGINT COMMENT '创建者',
    `update_by` BIGINT COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记',
    `tenant_id` VARCHAR(32) COMMENT '租户ID',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_form_id` (`form_id`),
    KEY `idx_version` (`form_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单版本表';

CREATE TABLE `wf_form_data` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `form_id` BIGINT NOT NULL COMMENT '表单ID',
    `form_version_id` BIGINT COMMENT '表单版本ID',
    `process_instance_id` VARCHAR(64) COMMENT '流程实例ID',
    `task_id` VARCHAR(64) COMMENT '任务ID',
    `business_key` VARCHAR(255) COMMENT '业务Key',
    `data_json` LONGTEXT COMMENT '表单数据JSON',
    `submit_user_id` BIGINT COMMENT '提交人ID',
    `submit_user_name` VARCHAR(64) COMMENT '提交人姓名',
    `submit_time` DATETIME COMMENT '提交时间',
    `create_by` BIGINT COMMENT '创建者',
    `update_by` BIGINT COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记',
    `tenant_id` VARCHAR(32) COMMENT '租户ID',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_form_id` (`form_id`),
    KEY `idx_process_instance_id` (`process_instance_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_business_key` (`business_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单数据表';

CREATE TABLE `sys_form_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `form_id` BIGINT NOT NULL COMMENT '表单ID',
    `field_code` VARCHAR(64) COMMENT '字段编码，为空表示表单级权限',
    `permission` VARCHAR(64) NOT NULL COMMENT '权限标识',
    `perm_type` VARCHAR(32) NOT NULL COMMENT '权限类型：view/edit/readonly/hidden',
    `description` VARCHAR(255) COMMENT '权限描述',
    `create_by` BIGINT COMMENT '创建者',
    `update_by` BIGINT COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记',
    `tenant_id` VARCHAR(32) COMMENT '租户ID',
    `remark` VARCHAR(500) COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_form_id` (`form_id`),
    KEY `idx_permission` (`permission`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单权限表';

CREATE TABLE `sys_role_form_permission` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `form_perm_id` BIGINT NOT NULL COMMENT '表单权限ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `form_perm_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_form_perm_id` (`form_perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表单权限关联表';
