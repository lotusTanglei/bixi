-- Persist tenant-local AI model defaults so a restart or another cloud
-- instance observes the same configuration. Provider credentials stay in
-- deployment secrets and are intentionally absent from this table.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_ai_model_config_20260928;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_ai_model_config_20260928()
BEGIN
    DECLARE object_count BIGINT DEFAULT 0;
    DECLARE column_count BIGINT DEFAULT 0;
    DECLARE definition_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;
    DECLARE index_column_count BIGINT DEFAULT 0;
    DECLARE index_non_unique BIGINT DEFAULT 0;
    DECLARE index_columns VARCHAR(255) DEFAULT NULL;
    DECLARE duplicate_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_model_config'
       AND table_type = 'BASE TABLE';

    IF object_count = 0 THEN
        CREATE TABLE ai_model_config (
            id BIGINT NOT NULL,
            current_model VARCHAR(64) NOT NULL DEFAULT 'qwen-plus',
            temperature DECIMAL(4,3) NOT NULL DEFAULT 0.700,
            max_tokens INT NOT NULL DEFAULT 2000,
            top_p DECIMAL(4,3) NOT NULL DEFAULT 0.900,
            system_prompt TEXT NULL,
            create_by BIGINT DEFAULT NULL,
            update_by BIGINT DEFAULT NULL,
            create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            del_flag CHAR(1) DEFAULT '0',
            status CHAR(1) DEFAULT '0',
            data_status CHAR(1) DEFAULT '0',
            tenant_id BIGINT NOT NULL,
            remark VARCHAR(500) DEFAULT NULL,
            PRIMARY KEY (id),
            UNIQUE KEY uk_ai_model_config_tenant (tenant_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          COMMENT='AI租户模型配置表';
    ELSE
        -- Existing installations created before this feature may have a
        -- partial table. Add only missing columns and reject incompatible
        -- definitions instead of silently changing operator-owned data.
        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'id';
        IF column_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_model_config.id is required before migration';
        END IF;
        SELECT COUNT(*) INTO definition_count
         FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'id' AND column_type = 'bigint' AND is_nullable = 'NO';
        IF definition_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.id definition';
        END IF;
        SELECT COUNT(*), GROUP_CONCAT(column_name ORDER BY seq_in_index)
          INTO index_column_count, index_columns
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND index_name = 'PRIMARY';
        IF index_column_count <> 1 OR index_columns <> 'id' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_model_config.id primary key is required';
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'current_model';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN current_model VARCHAR(64)
                NOT NULL DEFAULT 'qwen-plus' COMMENT '当前模型' AFTER id;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'current_model' AND column_type = 'varchar(64)'
               AND is_nullable = 'NO';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.current_model definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'temperature';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN temperature DECIMAL(4,3)
                NOT NULL DEFAULT 0.700 COMMENT '温度参数' AFTER current_model;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'temperature' AND column_type = 'decimal(4,3)'
               AND is_nullable = 'NO';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.temperature definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'max_tokens';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN max_tokens INT
                NOT NULL DEFAULT 2000 COMMENT '最大token数' AFTER temperature;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'max_tokens' AND data_type = 'int'
               AND is_nullable = 'NO';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.max_tokens definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'top_p';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN top_p DECIMAL(4,3)
                NOT NULL DEFAULT 0.900 COMMENT 'topP参数' AFTER max_tokens;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'top_p' AND column_type = 'decimal(4,3)'
               AND is_nullable = 'NO';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.top_p definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'system_prompt';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN system_prompt TEXT NULL
                COMMENT '系统提示词' AFTER top_p;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'system_prompt'
               AND data_type IN ('text', 'mediumtext', 'longtext');
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.system_prompt definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'create_by';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN create_by BIGINT DEFAULT NULL
                COMMENT '创建者' AFTER system_prompt;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'create_by' AND data_type = 'bigint';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.create_by definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'update_by';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN update_by BIGINT DEFAULT NULL
                COMMENT '修改者' AFTER create_by;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'update_by' AND data_type = 'bigint';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.update_by definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'create_time';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP
                COMMENT '创建时间' AFTER update_by;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'create_time' AND data_type = 'datetime';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.create_time definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'update_time';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP
                ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER create_time;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'update_time' AND data_type = 'datetime';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.update_time definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'del_flag';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN del_flag CHAR(1) DEFAULT '0'
                COMMENT '删除标记' AFTER update_time;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'del_flag' AND column_type = 'char(1)';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.del_flag definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'status';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN status CHAR(1) DEFAULT '0'
                COMMENT '业务状态' AFTER del_flag;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'status' AND column_type = 'char(1)';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.status definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'data_status';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN data_status CHAR(1) DEFAULT '0'
                COMMENT '数据库状态' AFTER status;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'data_status' AND column_type = 'char(1)';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.data_status definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'remark';
        IF column_count = 0 THEN
            ALTER TABLE ai_model_config ADD COLUMN remark VARCHAR(500) DEFAULT NULL
                COMMENT '备注' AFTER data_status;
        ELSE
            SELECT COUNT(*) INTO definition_count
              FROM information_schema.columns
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND column_name = 'remark' AND column_type = 'varchar(500)';
            IF definition_count <> 1 THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.remark definition';
            END IF;
        END IF;

        SELECT COUNT(*) INTO column_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'tenant_id';
        IF column_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_model_config.tenant_id is required before migration';
        END IF;
        SELECT COUNT(*) INTO definition_count
         FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'tenant_id' AND data_type = 'bigint'
           AND column_type = 'bigint';
        IF definition_count <> 1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting ai_model_config.tenant_id type';
        END IF;
        SELECT COUNT(*) INTO duplicate_count
          FROM ai_model_config
         WHERE tenant_id IS NULL;
        IF duplicate_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_model_config.tenant_id contains NULL values';
        END IF;
        SELECT COUNT(*) INTO duplicate_count
          FROM (SELECT tenant_id FROM ai_model_config GROUP BY tenant_id HAVING COUNT(*) > 1) duplicate_tenants;
        IF duplicate_count > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ai_model_config contains duplicate tenants';
        END IF;
        SELECT COUNT(*) INTO definition_count
          FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND column_name = 'tenant_id' AND is_nullable = 'NO';
        IF definition_count = 0 THEN
            ALTER TABLE ai_model_config MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '租户ID';
        END IF;

        SELECT COUNT(*) INTO index_count
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
           AND index_name = 'uk_ai_model_config_tenant';
        IF index_count > 0 THEN
            SELECT COUNT(*), MAX(non_unique), GROUP_CONCAT(column_name ORDER BY seq_in_index)
              INTO index_column_count, index_non_unique, index_columns
              FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'ai_model_config'
               AND index_name = 'uk_ai_model_config_tenant';
            IF index_column_count <> 1 OR index_non_unique <> 0 OR index_columns <> 'tenant_id' THEN
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting uk_ai_model_config_tenant index definition';
            END IF;
        ELSE
            ALTER TABLE ai_model_config
                ADD CONSTRAINT uk_ai_model_config_tenant UNIQUE (tenant_id);
        END IF;
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_ai_model_config_20260928();
DROP PROCEDURE bixi_migrate_ai_model_config_20260928;
