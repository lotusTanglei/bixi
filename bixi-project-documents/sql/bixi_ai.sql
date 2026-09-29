-- =====================================================
-- AI 模块数据库表
-- 版本: 1.0.0
-- 说明: AI 会话、消息、文档、向量嵌入等表
-- =====================================================

-- AI 租户模型配置表。每个租户只有一份当前配置；provider 密钥不落库。
DROP TABLE IF EXISTS `ai_model_config`;
CREATE TABLE `ai_model_config` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `current_model` VARCHAR(64) NOT NULL DEFAULT 'qwen-plus' COMMENT '当前模型',
    `temperature` DECIMAL(4,3) NOT NULL DEFAULT 0.700 COMMENT '温度参数',
    `max_tokens` INT NOT NULL DEFAULT 2000 COMMENT '最大token数',
    `top_p` DECIMAL(4,3) NOT NULL DEFAULT 0.900 COMMENT 'topP参数',
    `system_prompt` TEXT COMMENT '系统提示词',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '修改者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `status` CHAR(1) DEFAULT '0' COMMENT '业务状态',
    `data_status` CHAR(1) DEFAULT '0' COMMENT '数据库状态',
    `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_model_config_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI租户模型配置表';

-- AI 会话表
CREATE TABLE `ai_session` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `model` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型',
    `status` VARCHAR(32) DEFAULT 'active' COMMENT '会话状态：active/archived',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `data_status` CHAR(1) DEFAULT '0' COMMENT '数据库状态',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI会话表';

-- AI 消息表
CREATE TABLE `ai_message` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `session_id` BIGINT DEFAULT NULL COMMENT '会话ID',
    `role` VARCHAR(32) DEFAULT NULL COMMENT '角色：user/assistant',
    `content` TEXT COMMENT '消息内容',
    `token_count` INT DEFAULT NULL COMMENT 'token数量',
    `sources` TEXT COMMENT '引用来源JSON',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `status` CHAR(1) DEFAULT '0' COMMENT '业务状态',
    `data_status` CHAR(1) DEFAULT '0' COMMENT '数据库状态',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI消息表';

-- AI 对话记录表
CREATE TABLE `ai_conversation` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `session_id` VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
    `question` TEXT COMMENT '用户问题',
    `answer` TEXT COMMENT 'AI回答',
    `model` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型',
    `token_count` INT DEFAULT NULL COMMENT 'token消耗',
    `conversation_type` VARCHAR(32) DEFAULT NULL COMMENT '对话类型：chat/rag/stream',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `status` CHAR(1) DEFAULT '0' COMMENT '业务状态',
    `data_status` CHAR(1) DEFAULT '0' COMMENT '数据库状态',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI对话记录表';

-- AI 文档表
CREATE TABLE `ai_document` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `title` VARCHAR(255) DEFAULT NULL COMMENT '文档标题',
    `content` LONGTEXT COMMENT '文档内容',
    `source` VARCHAR(255) DEFAULT NULL COMMENT '文档来源',
    `doc_type` VARCHAR(64) DEFAULT NULL COMMENT '文档类型',
    `vector_status` TINYINT DEFAULT 0 COMMENT '向量状态：0-未向量化，1-已向量化',
    `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `status` CHAR(1) DEFAULT '0' COMMENT '业务状态',
    `data_status` CHAR(1) DEFAULT '0' COMMENT '数据库状态',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_vector_status` (`vector_status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI文档表';

-- AI 向量嵌入表
CREATE TABLE `ai_embedding` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `document_id` BIGINT DEFAULT NULL COMMENT '文档ID',
    `vector_id` VARCHAR(128) DEFAULT NULL COMMENT '向量ID（向量数据库中的ID）',
    `embedding_model` VARCHAR(64) DEFAULT NULL COMMENT '嵌入模型',
    `embedding` TEXT COMMENT '向量数据，JSON数组或逗号分隔数字',
    `dimension` INT DEFAULT NULL COMMENT '向量维度',
    `chunk_index` INT DEFAULT NULL COMMENT '分块索引',
    `chunk_content` LONGTEXT COMMENT '分块原文，用于来源追溯',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `status` CHAR(1) DEFAULT '0' COMMENT '业务状态',
    `data_status` CHAR(1) DEFAULT '0' COMMENT '数据库状态',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_vector_id` (`vector_id`),
    KEY `idx_embedding_document_chunk` (`document_id`, `chunk_index`, `del_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI向量嵌入表';
