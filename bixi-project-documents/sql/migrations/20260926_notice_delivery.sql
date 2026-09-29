-- Add recipient-level delivery evidence for existing notification tables.
-- Stop notice management writes and consumers while this migration runs.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_notice_delivery_20260926;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_notice_delivery_20260926()
BEGIN
    DECLARE object_count BIGINT DEFAULT 0;
    DECLARE definition_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;
    DECLARE index_columns VARCHAR(255) DEFAULT NULL;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_user_notice'
       AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_user_notice table is missing';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_status';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_status' AND column_type = 'varchar(16)'
       AND is_nullable = 'NO' AND column_default = 'PENDING'
       AND character_set_name = 'ascii' AND collation_name = 'ascii_bin';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_status definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_attempts';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_attempts' AND column_type = 'int'
       AND is_nullable = 'NO' AND column_default = '0';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_attempts definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_last_error';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_last_error' AND column_type = 'varchar(500)'
       AND is_nullable = 'YES' AND character_set_name = 'utf8mb4'
       AND collation_name = 'utf8mb4_general_ci';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_last_error definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_last_attempt_at';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_last_attempt_at' AND column_type = 'datetime'
       AND is_nullable = 'YES';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_last_attempt_at definition';
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_delivered_at';
    SELECT COUNT(*) INTO definition_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_delivered_at' AND column_type = 'datetime'
       AND is_nullable = 'YES';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_user_notice.delivery_delivered_at definition';
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND index_name = 'idx_sys_user_notice_delivery';
    IF index_count > 0 THEN
        SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
          INTO index_columns
          FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
           AND index_name = 'idx_sys_user_notice_delivery';
        IF index_columns <> 'notice_id,delivery_status,del_flag' THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting idx_sys_user_notice_delivery definition';
        END IF;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_status';
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
                NOT NULL DEFAULT 'PENDING' AFTER read_time;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_attempts';
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_attempts INT NOT NULL DEFAULT 0 AFTER delivery_status;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_last_error';
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_last_error VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
                DEFAULT NULL AFTER delivery_attempts;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_last_attempt_at';
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_last_attempt_at DATETIME DEFAULT NULL AFTER delivery_last_error;
    END IF;

    SELECT COUNT(*) INTO object_count FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND column_name = 'delivery_delivered_at';
    IF object_count = 0 THEN
        ALTER TABLE sys_user_notice
            ADD COLUMN delivery_delivered_at DATETIME DEFAULT NULL AFTER delivery_last_attempt_at;
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'sys_user_notice'
       AND index_name = 'idx_sys_user_notice_delivery';
    IF index_count = 0 THEN
        CREATE INDEX idx_sys_user_notice_delivery
            ON sys_user_notice(notice_id, delivery_status, del_flag);
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_notice_delivery_20260926();
DROP PROCEDURE bixi_migrate_notice_delivery_20260926;
