-- Add provider-neutral asynchronous receipt state for external notice channels.
-- Receipt callbacks are HMAC protected by the application; this migration only
-- stores bounded status/code/time evidence and remains repeatable.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_notice_receipts_20260928;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_notice_receipts_20260928()
BEGIN
    DECLARE object_count BIGINT DEFAULT 0;
    DECLARE definition_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_user_notice table is missing';
    END IF;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_receipt_status';
    SELECT COUNT(*) INTO definition_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_receipt_status'
       AND column_type = 'varchar(16)'
       AND is_nullable = 'YES'
       AND character_set_name = 'ascii'
       AND collation_name = 'ascii_bin';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_receipt_status definition';
    END IF;
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_receipt_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
                NULL COMMENT '第三方回执状态' AFTER delivery_delivered_at;
    END IF;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_receipt_code';
    SELECT COUNT(*) INTO definition_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_receipt_code'
       AND column_type = 'varchar(64)'
       AND is_nullable = 'YES'
       AND character_set_name = 'ascii'
       AND collation_name = 'ascii_bin';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_receipt_code definition';
    END IF;
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_receipt_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
                NULL COMMENT '第三方回执码（已脱敏）' AFTER delivery_receipt_status;
    END IF;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_receipt_at';
    SELECT COUNT(*) INTO definition_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_receipt_at'
       AND column_type = 'datetime'
       AND is_nullable = 'YES';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_receipt_at definition';
    END IF;
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_receipt_at DATETIME NULL COMMENT '第三方回执时间'
                AFTER delivery_receipt_code;
    END IF;

    SELECT COUNT(*) INTO index_count
      FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND index_name = 'idx_sys_user_notice_receipt';
    IF index_count = 0 THEN
        CREATE INDEX idx_sys_user_notice_receipt
            ON sys_user_notice (notice_id, delivery_receipt_status, del_flag);
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_notice_receipts_20260928();
DROP PROCEDURE bixi_migrate_notice_receipts_20260928;
