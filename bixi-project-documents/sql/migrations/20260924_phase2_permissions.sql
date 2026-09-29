-- Additive generator and Quartz permissions for existing MySQL 8.0+ databases.
-- Compatible existing menu presentation metadata is preserved; custom conflicts stop the migration.
DROP PROCEDURE IF EXISTS bixi_migrate_phase2_permissions_20260924;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_phase2_permissions_20260924()
BEGIN
    DECLARE conflict_id BIGINT;
    DECLARE conflict_message VARCHAR(180);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS bixi_phase2_permission_expected_20260924;
        RESIGNAL;
    END;

    START TRANSACTION;
    CREATE TEMPORARY TABLE bixi_phase2_permission_expected_20260924 LIKE sys_menu;
    INSERT INTO bixi_phase2_permission_expected_20260924
        (id, name, permission, parent_id, visible, sn, type, keep_alive, embedded, create_time)
    VALUES
        (2301, '查看生成配置', 'codegen_table_view', 2300, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (2302, '同步表结构', 'codegen_table_sync', 2300, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (2303, '编辑生成配置', 'codegen_table_edit', 2300, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (2304, '生成代码', 'codegen_table_generate', 2300, '0', 4, '1', '0', '0', CURRENT_TIMESTAMP),
        (2305, '导出生成配置', 'codegen_table_export', 2300, '0', 5, '1', '0', '0', CURRENT_TIMESTAMP),
        (2872, '查看任务', 'job_sys_job_view', 2800, '0', 8, '1', '0', '0', CURRENT_TIMESTAMP),
        (2873, '查看执行记录', 'job_sys_job_record_view', 2800, '0', 9, '1', '0', '0', CURRENT_TIMESTAMP),
        (2874, '删除执行记录', 'job_sys_job_record_del', 2800, '0', 10, '1', '0', '0', CURRENT_TIMESTAMP);

    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2300 AND path = '/gen/table/index' AND type = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase-two permissions require generator menu 2300';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 2800 AND path = '/job/job-manage/index' AND type = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase-two permissions require Quartz menu 2800';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM sys_role WHERE id = 1 AND code = 'ROLE_ADMIN' AND del_flag = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Phase-two permissions require administrator role 1';
    END IF;

    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_phase2_permission_expected_20260924 expected ON expected.id = existing.id
    WHERE NOT (CAST(existing.path AS BINARY) <=> CAST(expected.path AS BINARY))
       OR NOT (CAST(existing.permission AS BINARY) <=> CAST(expected.permission AS BINARY))
       OR NOT (existing.parent_id <=> expected.parent_id)
       OR NOT (CAST(existing.type AS BINARY) <=> CAST(expected.type AS BINARY));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Phase-two permission menu conflict at id ', conflict_id, '; reconcile custom menu IDs before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_phase2_permission_expected_20260924 expected ON existing.id <> expected.id
        AND CAST(existing.permission AS BINARY) = CAST(expected.permission AS BINARY);
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('Phase-two permission identity already used at id ', conflict_id, '; reconcile custom permissions before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    INSERT INTO sys_menu
        (id, name, permission, parent_id, visible, sn, type, keep_alive, embedded, create_time)
    SELECT expected.id, expected.name, expected.permission, expected.parent_id, expected.visible,
           expected.sn, expected.type, expected.keep_alive, expected.embedded, expected.create_time
    FROM bixi_phase2_permission_expected_20260924 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.id = expected.id);

    INSERT INTO sys_role_menu (role_id, menu_id, create_time)
    SELECT 1, expected.id, CURRENT_TIMESTAMP
    FROM bixi_phase2_permission_expected_20260924 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id = 1 AND existing.menu_id = expected.id);

    DROP TEMPORARY TABLE bixi_phase2_permission_expected_20260924;
    COMMIT;
END$$
DELIMITER ;
CALL bixi_migrate_phase2_permissions_20260924();
DROP PROCEDURE bixi_migrate_phase2_permissions_20260924;
