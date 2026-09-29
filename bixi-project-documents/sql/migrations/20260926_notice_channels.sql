-- Add the selected outbound channel to published notices.
-- Existing rows remain on the verified in-app SSE path.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_notice_channels_20260926;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_notice_channels_20260926()
BEGIN
    DECLARE object_count BIGINT DEFAULT 0;
    DECLARE definition_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_notice'
       AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_notice table is missing';
    END IF;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_notice'
       AND column_name = 'delivery_channel';
    SELECT COUNT(*) INTO definition_count
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'sys_notice'
       AND column_name = 'delivery_channel'
       AND column_type = 'varchar(16)'
       AND is_nullable = 'NO'
       AND column_default = 'IN_APP'
       AND character_set_name = 'ascii'
       AND collation_name = 'ascii_bin';
    IF object_count > 0 AND definition_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Conflicting sys_notice.delivery_channel definition';
    END IF;

    IF object_count = 0 THEN
        ALTER TABLE sys_notice
            ADD COLUMN delivery_channel VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin
                NOT NULL DEFAULT 'IN_APP' AFTER priority;
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_notice_channels_20260926();
DROP PROCEDURE bixi_migrate_notice_channels_20260926;
