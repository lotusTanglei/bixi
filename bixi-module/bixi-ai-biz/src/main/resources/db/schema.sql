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
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
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
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
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
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
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
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
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
    `dimension` INT DEFAULT NULL COMMENT '向量维度',
    `chunk_index` INT DEFAULT NULL COMMENT '分块索引',
    `create_by` BIGINT DEFAULT NULL COMMENT '创建者',
    `update_by` BIGINT DEFAULT NULL COMMENT '更新者',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` CHAR(1) DEFAULT '0' COMMENT '删除标记：0-正常，1-删除',
    `tenant_id` VARCHAR(32) DEFAULT NULL COMMENT '租户ID',
    PRIMARY KEY (`id`),
    KEY `idx_document_id` (`document_id`),
    KEY `idx_vector_id` (`vector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI向量嵌入表';
