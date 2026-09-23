-- Additive workflow menus. Run in a dedicated MySQL 8.0+ maintenance session.
-- Existing menu identities must match; presentation/custom metadata is never overwritten.
-- The procedure keeps every persistent menu/role write behind preflight validation,
-- including when the client is configured to continue after errors (mysql --force).
DROP PROCEDURE IF EXISTS bixi_migrate_workflow_menus_20260921;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_workflow_menus_20260921()
BEGIN
    DECLARE conflict_id BIGINT;
    DECLARE conflict_message VARCHAR(128);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS bixi_workflow_menu_expected_20260921;
        RESIGNAL;
    END;

    START TRANSACTION;
    CREATE TEMPORARY TABLE bixi_workflow_menu_expected_20260921 LIKE sys_menu;
    INSERT INTO bixi_workflow_menu_expected_20260921
        (id, name, en_name, permission, path, parent_id, icon, visible, sn, type, keep_alive, embedded, create_time)
    VALUES
        (5010, '请假申请', 'leave', NULL, '/demo/leave/index', 5000, 'ele-Calendar', '1', 2, '0', '0', '0', CURRENT_TIMESTAMP),
        (5011, '请假查看', NULL, 'demo_leave_view', NULL, 5010, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (5012, '新建请假', NULL, 'demo_leave_add', NULL, 5010, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (5013, '修改及提交请假', NULL, 'demo_leave_edit', NULL, 5010, NULL, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (5014, '删除请假草稿', NULL, 'demo_leave_del', NULL, 5010, NULL, '0', 4, '1', '0', '0', CURRENT_TIMESTAMP),
        (6000, '工作流', 'workflow', NULL, '/workflow', -1, 'ele-Connection', '1', 3, '0', '0', '0', CURRENT_TIMESTAMP),
        (6001, '我的待办', 'todo', NULL, '/workflow/task/todo', 6000, 'ele-DocumentChecked', '1', 1, '0', '0', '0', CURRENT_TIMESTAMP),
        (6002, '我的已办', 'done', NULL, '/workflow/task/done', 6000, 'ele-Finished', '1', 2, '0', '0', '0', CURRENT_TIMESTAMP),
        (6003, '我发起的流程', 'instance', NULL, '/workflow/process/instance', 6000, 'ele-List', '1', 3, '0', '0', '0', CURRENT_TIMESTAMP),
        (6004, '流程定义', 'definition', NULL, '/workflow/definition/index', 6000, 'ele-SetUp', '1', 4, '0', '0', '0', CURRENT_TIMESTAMP),
        (6005, '可靠投递', 'recovery', NULL, '/workflow/recovery/index', 6000, 'ele-Refresh', '1', 5, '0', '0', '0', CURRENT_TIMESTAMP),
        (6011, '查看任务', NULL, 'workflow_task_view', NULL, 6001, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (6012, '办理任务', NULL, 'workflow_task_edit', NULL, 6001, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (6021, '查看流程', NULL, 'workflow_process_view', NULL, 6003, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (6022, '发起流程', NULL, 'workflow_process_add', NULL, 6003, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (6023, '管理本人流程', NULL, 'workflow_process_edit', NULL, 6003, NULL, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (6031, '查看流程定义', NULL, 'workflow_definition_view', NULL, 6004, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (6032, '部署及管理流程定义', NULL, 'workflow_definition_edit', NULL, 6004, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (6041, '查看可靠投递', NULL, 'workflow_recovery_view', NULL, 6005, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (6042, '恢复可靠投递', NULL, 'workflow_recovery_edit', NULL, 6005, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP);

    -- The existing demo directory is a required parent; validate it without adding/granting it.
    INSERT INTO bixi_workflow_menu_expected_20260921 (id, path, parent_id, type)
    VALUES (5000, '/demo', -1, '0');
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 5000) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Workflow menus require demo parent menu 5000';
    END IF;

    -- Compare nullable identity fields exactly, independent of the database's case-insensitive collation.
    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_workflow_menu_expected_20260921 expected ON expected.id = existing.id
    WHERE NOT (CAST(existing.path AS BINARY) <=> CAST(expected.path AS BINARY))
       OR NOT (CAST(existing.permission AS BINARY) <=> CAST(expected.permission AS BINARY))
       OR NOT (existing.parent_id <=> expected.parent_id)
       OR NOT (CAST(existing.type AS BINARY) <=> CAST(expected.type AS BINARY));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Workflow menu conflict at id ', conflict_id, '; reconcile custom menu IDs before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    -- A custom installation may already use these routes/permissions at different IDs.
    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_workflow_menu_expected_20260921 expected ON existing.id <> expected.id
        AND ((expected.path IS NOT NULL AND CAST(existing.path AS BINARY) = CAST(expected.path AS BINARY))
          OR (expected.permission IS NOT NULL AND CAST(existing.permission AS BINARY) = CAST(expected.permission AS BINARY)));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Workflow menu identity already used at id ', conflict_id, '; reconcile custom menu IDs before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    INSERT INTO sys_menu
        (id, name, en_name, permission, path, parent_id, icon, visible, sn, type, keep_alive, embedded, create_time)
    SELECT expected.id, expected.name, expected.en_name, expected.permission, expected.path,
           expected.parent_id, expected.icon, expected.visible, expected.sn, expected.type,
           expected.keep_alive, expected.embedded, expected.create_time
    FROM bixi_workflow_menu_expected_20260921 expected
    WHERE expected.id <> 5000
      AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.id = expected.id);

    INSERT INTO sys_role_menu (role_id, menu_id, create_time)
    SELECT 1, expected.id, CURRENT_TIMESTAMP
    FROM bixi_workflow_menu_expected_20260921 expected
    WHERE expected.id <> 5000
      AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = 1 AND existing.menu_id = expected.id);

    DROP TEMPORARY TABLE bixi_workflow_menu_expected_20260921;
    COMMIT;
END$$
DELIMITER ;
CALL bixi_migrate_workflow_menus_20260921();
DROP PROCEDURE bixi_migrate_workflow_menus_20260921;
