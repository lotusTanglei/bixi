-- Prevent concurrent installation of the same fixed template revision.
-- Run with generator group writes paused. Duplicate active names are never
-- rewritten automatically because an operator must decide which group wins.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_generator_group_uniqueness_20260924;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_generator_group_uniqueness_20260924()
BEGIN
    DECLARE duplicate_count BIGINT DEFAULT 0;
    DECLARE column_count BIGINT DEFAULT 0;
    DECLARE compatible_column_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;
    DECLARE compatible_index_count BIGINT DEFAULT 0;
	DECLARE index_part_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT group_name FROM gen_group
        WHERE del_flag = '0' AND group_name IS NOT NULL
        GROUP BY group_name HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate active generator group names require manual resolution';
    END IF;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'gen_group'
      AND column_name = 'active_group_name';

    SELECT COUNT(*) INTO compatible_column_count
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'gen_group'
      AND column_name = 'active_group_name'
      AND data_type = 'varchar'
      AND character_maximum_length = 255
      AND extra LIKE '%STORED GENERATED%'
      AND LOWER(generation_expression) LIKE '%del_flag%'
      AND LOWER(generation_expression) LIKE '%group_name%';

    IF column_count = 0 THEN
        ALTER TABLE gen_group
            ADD COLUMN active_group_name varchar(255)
                CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
                GENERATED ALWAYS AS (IF(del_flag = '0', group_name, NULL)) STORED
                COMMENT '未删除分组唯一名称'
                AFTER group_name;
    ELSEIF compatible_column_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Existing gen_group.active_group_name is incompatible';
    END IF;

    SELECT COUNT(DISTINCT index_name) INTO index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'gen_group'
      AND index_name = 'uk_gen_group_active_name';

    SELECT COUNT(DISTINCT index_name) INTO compatible_index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'gen_group'
      AND index_name = 'uk_gen_group_active_name'
      AND non_unique = 0
      AND seq_in_index = 1
      AND column_name = 'active_group_name';

	SELECT COUNT(*) INTO index_part_count
	FROM information_schema.statistics
	WHERE table_schema = DATABASE()
	  AND table_name = 'gen_group'
	  AND index_name = 'uk_gen_group_active_name';

    IF index_count = 0 THEN
        ALTER TABLE gen_group
            ADD CONSTRAINT uk_gen_group_active_name UNIQUE (active_group_name);
	ELSEIF compatible_index_count <> 1 OR index_part_count <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Existing uk_gen_group_active_name is incompatible';
    END IF;
END$$
DELIMITER ;
CALL bixi_migrate_generator_group_uniqueness_20260924();
DROP PROCEDURE bixi_migrate_generator_group_uniqueness_20260924;
