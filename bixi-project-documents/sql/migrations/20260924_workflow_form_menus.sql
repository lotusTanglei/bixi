-- Additive workflow form administration menus for existing MySQL 8.0+ databases.
-- Existing identities must match; presentation metadata is left untouched.
DROP PROCEDURE IF EXISTS bixi_migrate_workflow_form_menus_20260924;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_workflow_form_menus_20260924()
BEGIN
    DECLARE conflict_id BIGINT;
    DECLARE conflict_message VARCHAR(160);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS bixi_workflow_form_menu_expected_20260924;
        RESIGNAL;
    END;

    START TRANSACTION;
    CREATE TEMPORARY TABLE bixi_workflow_form_menu_expected_20260924 LIKE sys_menu;
    INSERT INTO bixi_workflow_form_menu_expected_20260924
        (id, name, en_name, permission, path, parent_id, icon, visible, sn, type, keep_alive, embedded, create_time)
    VALUES
        (6006, '表单管理', 'form', NULL, '/workflow/form/index', 6000, 'ele-Document', '1', 6, '0', '0', '0', CURRENT_TIMESTAMP),
        (6007, '表单设计器', 'formDesigner', NULL, '/workflow/form/designer', 6000, NULL, '0', 7, '0', '0', '0', CURRENT_TIMESTAMP),
        (6008, '表单版本', 'formVersion', NULL, '/workflow/form/version', 6000, NULL, '0', 8, '0', '0', '0', CURRENT_TIMESTAMP),
        (6009, '字段权限', 'formPermission', NULL, '/workflow/form/permission', 6000, NULL, '0', 9, '0', '0', '0', CURRENT_TIMESTAMP),
        (6051, '查看表单', NULL, 'workflow_form_view', NULL, 6006, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (6052, '新增表单', NULL, 'workflow_form_add', NULL, 6006, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (6053, '编辑表单', NULL, 'workflow_form_edit', NULL, 6006, NULL, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (6054, '删除表单', NULL, 'workflow_form_del', NULL, 6006, NULL, '0', 4, '1', '0', '0', CURRENT_TIMESTAMP);

    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6000 AND path = '/workflow' AND type = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Workflow form menus require workflow parent menu 6000';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ROLE_ADMIN' AND del_flag = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Workflow form menus require administrator role 1';
    END IF;

    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_workflow_form_menu_expected_20260924 expected ON expected.id = existing.id
    WHERE NOT (CAST(existing.path AS BINARY) <=> CAST(expected.path AS BINARY))
       OR NOT (CAST(existing.permission AS BINARY) <=> CAST(expected.permission AS BINARY))
       OR NOT (existing.parent_id <=> expected.parent_id)
       OR NOT (CAST(existing.type AS BINARY) <=> CAST(expected.type AS BINARY));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Workflow form menu conflict at id ', conflict_id, '; reconcile custom menu IDs before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_workflow_form_menu_expected_20260924 expected ON existing.id <> expected.id
        AND ((expected.path IS NOT NULL AND CAST(existing.path AS BINARY) = CAST(expected.path AS BINARY))
          OR (expected.permission IS NOT NULL AND CAST(existing.permission AS BINARY) = CAST(expected.permission AS BINARY)));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Workflow form menu identity already used at id ', conflict_id, '; reconcile custom menu IDs before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    INSERT INTO sys_menu
        (id, name, en_name, permission, path, parent_id, icon, visible, sn, type, keep_alive, embedded, create_time)
    SELECT expected.id, expected.name, expected.en_name, expected.permission, expected.path,
           expected.parent_id, expected.icon, expected.visible, expected.sn, expected.type,
           expected.keep_alive, expected.embedded, expected.create_time
    FROM bixi_workflow_form_menu_expected_20260924 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.id = expected.id);

    INSERT INTO sys_role_menu (role_id, menu_id, create_time)
    SELECT 1, expected.id, CURRENT_TIMESTAMP
    FROM bixi_workflow_form_menu_expected_20260924 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = 1 AND existing.menu_id = expected.id);

    DROP TEMPORARY TABLE bixi_workflow_form_menu_expected_20260924;
    COMMIT;
END$$
DELIMITER ;
CALL bixi_migrate_workflow_form_menus_20260924();
DROP PROCEDURE bixi_migrate_workflow_form_menus_20260924;
