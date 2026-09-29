-- Align the AI tables with the audit/status fields inherited from BaseEntity.
-- The procedure is idempotent so it can be applied to existing installations
-- that were initialized before the AI mapper started using these fields.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_ai_base_entity_columns_20260926;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_ai_base_entity_columns_20260926()
BEGIN
    DECLARE column_count BIGINT DEFAULT 0;

    -- ai_session already has a business status (active/archived); only the
    -- remaining inherited columns are missing from the original schema.
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_session'
      AND column_name = 'data_status';
    IF column_count = 0 THEN
        ALTER TABLE ai_session ADD COLUMN data_status CHAR(1) DEFAULT '0'
            COMMENT '数据库状态' AFTER del_flag;
    END IF;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_session'
      AND column_name = 'remark';
    IF column_count = 0 THEN
        ALTER TABLE ai_session ADD COLUMN remark VARCHAR(500) DEFAULT NULL
            COMMENT '备注' AFTER tenant_id;
    END IF;

    -- The other AI entities inherit status, data_status and remark directly.
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_message'
      AND column_name = 'status';
    IF column_count = 0 THEN
        ALTER TABLE ai_message ADD COLUMN status CHAR(1) DEFAULT '0'
            COMMENT '业务状态' AFTER del_flag;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_message'
      AND column_name = 'data_status';
    IF column_count = 0 THEN
        ALTER TABLE ai_message ADD COLUMN data_status CHAR(1) DEFAULT '0'
            COMMENT '数据库状态' AFTER status;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_message'
      AND column_name = 'remark';
    IF column_count = 0 THEN
        ALTER TABLE ai_message ADD COLUMN remark VARCHAR(500) DEFAULT NULL
            COMMENT '备注' AFTER tenant_id;
    END IF;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_conversation'
      AND column_name = 'status';
    IF column_count = 0 THEN
        ALTER TABLE ai_conversation ADD COLUMN status CHAR(1) DEFAULT '0'
            COMMENT '业务状态' AFTER del_flag;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_conversation'
      AND column_name = 'data_status';
    IF column_count = 0 THEN
        ALTER TABLE ai_conversation ADD COLUMN data_status CHAR(1) DEFAULT '0'
            COMMENT '数据库状态' AFTER status;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_conversation'
      AND column_name = 'remark';
    IF column_count = 0 THEN
        ALTER TABLE ai_conversation ADD COLUMN remark VARCHAR(500) DEFAULT NULL
            COMMENT '备注' AFTER tenant_id;
    END IF;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_document'
      AND column_name = 'status';
    IF column_count = 0 THEN
        ALTER TABLE ai_document ADD COLUMN status CHAR(1) DEFAULT '0'
            COMMENT '业务状态' AFTER del_flag;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_document'
      AND column_name = 'data_status';
    IF column_count = 0 THEN
        ALTER TABLE ai_document ADD COLUMN data_status CHAR(1) DEFAULT '0'
            COMMENT '数据库状态' AFTER status;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_document'
      AND column_name = 'remark';
    IF column_count = 0 THEN
        ALTER TABLE ai_document ADD COLUMN remark VARCHAR(500) DEFAULT NULL
            COMMENT '备注' AFTER tenant_id;
    END IF;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
      AND column_name = 'status';
    IF column_count = 0 THEN
        ALTER TABLE ai_embedding ADD COLUMN status CHAR(1) DEFAULT '0'
            COMMENT '业务状态' AFTER del_flag;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
      AND column_name = 'data_status';
    IF column_count = 0 THEN
        ALTER TABLE ai_embedding ADD COLUMN data_status CHAR(1) DEFAULT '0'
            COMMENT '数据库状态' AFTER status;
    END IF;
    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
      AND column_name = 'remark';
    IF column_count = 0 THEN
        ALTER TABLE ai_embedding ADD COLUMN remark VARCHAR(500) DEFAULT NULL
            COMMENT '备注' AFTER tenant_id;
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_ai_base_entity_columns_20260926();
DROP PROCEDURE bixi_migrate_ai_base_entity_columns_20260926;
