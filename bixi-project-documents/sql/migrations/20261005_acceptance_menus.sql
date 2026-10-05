-- Add the Acceptance dictAggregate and publicParam menus to existing databases.
-- Run while menu and role permission writes are paused. Existing display fields are
-- preserved when the canonical identity already exists.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_acceptance_menus_20261005;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_acceptance_menus_20261005()
BEGIN
    DECLARE object_count BIGINT DEFAULT 0;
    DECLARE identity_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_menu table is missing';
    END IF;
    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'sys_role_menu' AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_role_menu table is missing';
    END IF;
    SELECT COUNT(*) INTO object_count
      FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND table_type = 'BASE TABLE';
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Required sys_role table is missing';
    END IF;
    SELECT COUNT(*) INTO object_count FROM sys_menu WHERE id = 2000;
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acceptance menus require system parent menu 2000';
    END IF;
    SELECT COUNT(*) INTO object_count FROM sys_role WHERE id = 1;
    IF object_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acceptance menus require administrator role 1';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS bixi_acceptance_menu_seed;
    CREATE TEMPORARY TABLE bixi_acceptance_menu_seed (
        id BIGINT NOT NULL,
        name VARCHAR(64) NOT NULL,
        permission VARCHAR(128) NULL,
        path VARCHAR(128) NULL,
        parent_id BIGINT NOT NULL,
        icon VARCHAR(64) NULL,
        visible CHAR(1) NOT NULL,
        sn INT NOT NULL,
        type CHAR(1) NOT NULL,
        PRIMARY KEY (id)
    ) ENGINE = MEMORY;

    INSERT INTO bixi_acceptance_menu_seed
        (id, name, permission, path, parent_id, icon, visible, sn, type)
    VALUES
        (7200, '字典表', NULL, '/acceptance/dictAggregate/index', 2000, 'ele-List', '1', 90, '0'),
        (7201, '查看', 'acceptance_dict_aggregate_view', NULL, 7200, NULL, '0', 1, '1'),
        (7202, '新增', 'acceptance_dict_aggregate_add', NULL, 7200, NULL, '0', 2, '1'),
        (7203, '修改', 'acceptance_dict_aggregate_edit', NULL, 7200, NULL, '0', 3, '1'),
        (7204, '删除', 'acceptance_dict_aggregate_del', NULL, 7200, NULL, '0', 4, '1'),
        (7205, '导入', 'acceptance_dict_aggregate_import', NULL, 7200, NULL, '0', 5, '1'),
        (7206, '导出', 'acceptance_dict_aggregate_export', NULL, 7200, NULL, '0', 6, '1'),
        (7207, '明细新增', 'acceptance_sys_dict_item_add', NULL, 7200, NULL, '0', 7, '1'),
        (7208, '明细修改', 'acceptance_sys_dict_item_edit', NULL, 7200, NULL, '0', 8, '1'),
        (7209, '明细删除', 'acceptance_sys_dict_item_del', NULL, 7200, NULL, '0', 9, '1'),
        (7210, '公共参数配置表', NULL, '/acceptance/publicParam/index', 2000, 'ele-List', '1', 91, '0'),
        (7211, '查看', 'acceptance_public_param_view', NULL, 7210, NULL, '0', 1, '1'),
        (7212, '新增', 'acceptance_public_param_add', NULL, 7210, NULL, '0', 2, '1'),
        (7213, '修改', 'acceptance_public_param_edit', NULL, 7210, NULL, '0', 3, '1'),
        (7214, '删除', 'acceptance_public_param_del', NULL, 7210, NULL, '0', 4, '1'),
        (7215, '导入', 'acceptance_public_param_import', NULL, 7210, NULL, '0', 5, '1'),
        (7216, '导出', 'acceptance_public_param_export', NULL, 7210, NULL, '0', 6, '1');

    -- Validate every existing ID and every canonical permission/path before any
    -- insert. This keeps a conflicting migration atomic under autocommit.
    SELECT COUNT(*) INTO identity_count
      FROM bixi_acceptance_menu_seed s
      JOIN sys_menu m ON m.id = s.id
     WHERE NOT (m.parent_id <=> s.parent_id)
        OR NOT (m.type <=> s.type)
        OR NOT (m.permission <=> s.permission)
        OR NOT (m.path <=> s.path);
    IF identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acceptance menu ID is occupied by a conflicting identity';
    END IF;

    SELECT COUNT(*) INTO identity_count
      FROM bixi_acceptance_menu_seed s
      JOIN sys_menu m
        ON ((s.permission IS NOT NULL AND m.permission = s.permission)
         OR (s.path IS NOT NULL AND m.path = s.path))
       AND m.id <> s.id;
    IF identity_count <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acceptance menu identity is already used by another ID';
    END IF;

    INSERT INTO sys_menu
        (id, name, permission, path, parent_id, icon, visible, sn, keep_alive, embedded, type,
         create_time, del_flag, status, data_status, tenant_id)
    SELECT s.id, s.name, s.permission, s.path, s.parent_id, s.icon, s.visible, s.sn, '0', '0', s.type,
           CURRENT_TIMESTAMP, '0', '0', '0', 1
      FROM bixi_acceptance_menu_seed s
      LEFT JOIN sys_menu m ON m.id = s.id
     WHERE m.id IS NULL;

    INSERT IGNORE INTO sys_role_menu (role_id, menu_id, create_time)
    SELECT 1, s.id, CURRENT_TIMESTAMP
      FROM bixi_acceptance_menu_seed s;
    DROP TEMPORARY TABLE bixi_acceptance_menu_seed;
END$$
DELIMITER ;
CALL bixi_migrate_acceptance_menus_20261005();
DROP PROCEDURE bixi_migrate_acceptance_menus_20261005;
