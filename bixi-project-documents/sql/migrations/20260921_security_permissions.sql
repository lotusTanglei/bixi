-- Add the 18 security permission menus and role 1 grants to an existing Bixi database.
-- Run in a dedicated MySQL 8.0+ maintenance connection with menu/grant writes paused.
-- Existing compatible rows and custom metadata are preserved; conflicts fail before writes.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_security_permissions_20260921;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_security_permissions_20260921()
BEGIN
    DECLARE conflict_id BIGINT;
    DECLARE conflict_message VARCHAR(128);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS bixi_security_menu_expected_20260921;
        DROP TEMPORARY TABLE IF EXISTS bixi_security_parent_expected_20260921;
        RESIGNAL;
    END;

    START TRANSACTION;
    IF NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 1
            AND CAST(code AS BINARY) = CAST('ROLE_ADMIN' AS BINARY) AND del_flag = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Security permissions require role 1 ROLE_ADMIN';
    END IF;

    CREATE TEMPORARY TABLE bixi_security_menu_expected_20260921 LIKE sys_menu;
    INSERT INTO bixi_security_menu_expected_20260921
        (id, name, permission, parent_id, visible, sn, type, keep_alive, embedded, create_time)
    VALUES
        (1106, '用户查看', 'sys_user_view', 1100, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1107, '用户导入', 'sys_user_import', 1100, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1204, '菜单查看', 'sys_menu_view', 1200, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1306, '角色查看', 'sys_role_view', 1300, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1307, '角色导入', 'sys_role_import', 1300, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1404, '部门查看', 'sys_dept_view', 1400, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1405, '部门导出', 'sys_dept_export', 1400, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (1406, '部门导入', 'sys_dept_import', 1400, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (2103, '日志查看', 'sys_log_view', 2100, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (2404, '终端查看', 'sys_client_view', 2400, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (2405, '终端导出', 'sys_client_export', 2400, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (2602, '在线用户查看', 'sys_token_view', 2600, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (4003, '缓存监控查看', 'sys_system_view', 4002, '0', 0, '1', '0', '0', CURRENT_TIMESTAMP),
        (2901, '通知查看', 'sys_notice_view', 2003471392852377602, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (2902, '通知新增', 'sys_notice_add', 2003471392852377602, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (2903, '通知编辑', 'sys_notice_edit', 2003471392852377602, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (2904, '通知删除', 'sys_notice_del', 2003471392852377602, '0', 4, '1', '0', '0', CURRENT_TIMESTAMP),
        (2905, '通知发送', 'sys_notice_send', 2003471392852377602, '0', 5, '1', '0', '0', CURRENT_TIMESTAMP);

    -- Parent menus are prerequisites, not new entries to create or grant.
    CREATE TEMPORARY TABLE bixi_security_parent_expected_20260921 LIKE sys_menu;
    INSERT INTO bixi_security_parent_expected_20260921 (id, path, parent_id, type)
    VALUES
        (1100, '/admin/user/index', 1000, '0'),
        (1200, '/admin/menu/index', 1000, '0'),
        (1300, '/admin/role/index', 1000, '0'),
        (1400, '/admin/dept/index', 1000, '0'),
        (2100, '/admin/log/index', 2001, '0'),
        (2400, '/admin/client/index', 2000, '0'),
        (2600, '/admin/token/index', 2000, '0'),
        (4002, '/ext/cache', 4000, '0'),
        (2003471392852377602, '/admin/notice/index', 2000, '0');

    SELECT MIN(expected.id) INTO conflict_id FROM bixi_security_parent_expected_20260921 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.id = expected.id);
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Security permissions require parent menu ', conflict_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;
    SELECT MIN(existing.id) INTO conflict_id FROM sys_menu existing
    JOIN bixi_security_parent_expected_20260921 expected ON existing.id = expected.id
    WHERE NOT (CAST(existing.path AS BINARY) <=> CAST(expected.path AS BINARY))
       OR NOT (CAST(existing.permission AS BINARY) <=> CAST(expected.permission AS BINARY))
       OR NOT (existing.parent_id <=> expected.parent_id)
       OR NOT (CAST(existing.type AS BINARY) <=> CAST(expected.type AS BINARY));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Security parent menu conflict at id ', conflict_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    SELECT MIN(existing.id) INTO conflict_id FROM sys_menu existing
    JOIN bixi_security_menu_expected_20260921 expected ON existing.id = expected.id
    WHERE NOT (CAST(existing.path AS BINARY) <=> CAST(expected.path AS BINARY))
       OR NOT (CAST(existing.permission AS BINARY) <=> CAST(expected.permission AS BINARY))
       OR NOT (existing.parent_id <=> expected.parent_id)
       OR NOT (CAST(existing.type AS BINARY) <=> CAST(expected.type AS BINARY));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Security menu conflict at id ', conflict_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;
    SELECT MIN(existing.id) INTO conflict_id FROM sys_menu existing
    JOIN bixi_security_menu_expected_20260921 expected
        ON existing.id <> expected.id AND existing.permission = expected.permission;
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Security permission already used at id ', conflict_id);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    INSERT INTO sys_menu
        (id, name, permission, parent_id, visible, sn, type, keep_alive, embedded, create_time)
    SELECT expected.id, expected.name, expected.permission, expected.parent_id, expected.visible,
           expected.sn, expected.type, expected.keep_alive, expected.embedded, expected.create_time
    FROM bixi_security_menu_expected_20260921 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.id = expected.id);
    INSERT INTO sys_role_menu (role_id, menu_id, create_time)
    SELECT 1, expected.id, CURRENT_TIMESTAMP FROM bixi_security_menu_expected_20260921 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing
                      WHERE existing.role_id = 1 AND existing.menu_id = expected.id);

    DROP TEMPORARY TABLE bixi_security_menu_expected_20260921;
    DROP TEMPORARY TABLE bixi_security_parent_expected_20260921;
    COMMIT;
END$$
DELIMITER ;
CALL bixi_migrate_security_permissions_20260921();
DROP PROCEDURE bixi_migrate_security_permissions_20260921;
