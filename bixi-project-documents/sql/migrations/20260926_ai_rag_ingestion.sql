-- Add chunk provenance for AI document ingestion and deterministic local embeddings.
-- The migration is additive and can be re-run on an existing MySQL 8.0+ database.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_ai_rag_ingestion_20260926;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_ai_rag_ingestion_20260926()
BEGIN
    DECLARE column_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;
    DECLARE index_columns VARCHAR(255) DEFAULT NULL;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
      AND column_name = 'embedding';
    IF column_count = 0 THEN
        ALTER TABLE ai_embedding ADD COLUMN embedding TEXT NULL
            COMMENT '向量数据，JSON数组或逗号分隔数字' AFTER embedding_model;
    ELSE
        SELECT COUNT(*) INTO column_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
          AND column_name = 'embedding'
          AND data_type IN ('text', 'mediumtext', 'longtext');
        IF column_count = 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_embedding.embedding definition';
        END IF;
    END IF;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
      AND column_name = 'chunk_content';
    IF column_count = 0 THEN
        ALTER TABLE ai_embedding ADD COLUMN chunk_content LONGTEXT NULL COMMENT '分块原文，用于来源追溯'
            AFTER chunk_index;
    ELSE
        SELECT COUNT(*) INTO column_count
        FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
          AND column_name = 'chunk_content'
          AND data_type IN ('text', 'mediumtext', 'longtext');
        IF column_count = 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_embedding.chunk_content definition';
        END IF;
    END IF;

    SELECT COUNT(*) INTO index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
      AND index_name = 'idx_embedding_document_chunk';
    IF index_count > 0 THEN
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) INTO index_columns
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'ai_embedding'
          AND index_name = 'idx_embedding_document_chunk';
        IF index_columns <> 'document_id,chunk_index,del_flag' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting idx_embedding_document_chunk definition';
        END IF;
    ELSE
        ALTER TABLE ai_embedding ADD INDEX idx_embedding_document_chunk (document_id, chunk_index, del_flag);
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_ai_rag_ingestion_20260926();
DROP PROCEDURE bixi_migrate_ai_rag_ingestion_20260926;
