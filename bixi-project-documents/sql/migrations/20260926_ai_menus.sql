-- Additive AI menu and permission migration for existing MySQL 8.0+ databases.
-- Menu identity conflicts fail before any persistent rows are written.
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS bixi_migrate_ai_menus_20260926;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_ai_menus_20260926()
BEGIN
    DECLARE conflict_id BIGINT;
    DECLARE conflict_message VARCHAR(180);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS bixi_ai_menu_expected_20260926;
        RESIGNAL;
    END;

    START TRANSACTION;
    IF NOT EXISTS (SELECT 1 FROM sys_role
                   WHERE id = 1 AND CAST(code AS BINARY) = CAST('ROLE_ADMIN' AS BINARY)
                     AND del_flag = '0') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AI menus require administrator role 1';
    END IF;

    CREATE TEMPORARY TABLE bixi_ai_menu_expected_20260926 LIKE sys_menu;
    INSERT INTO bixi_ai_menu_expected_20260926
        (id, name, en_name, permission, path, parent_id, icon, visible, sn, type, keep_alive, embedded, create_time)
    VALUES
        (7000, 'AI服务', 'ai', NULL, '/ai', -1, 'ele-ChatDotRound', '1', 4, '0', '0', '0', CURRENT_TIMESTAMP),
        (7001, 'AI对话', 'chat', NULL, '/ai/chat', 7000, 'ele-ChatDotRound', '1', 1, '0', '0', '0', CURRENT_TIMESTAMP),
        (7002, 'AI知识库', 'knowledge', NULL, '/ai/knowledge', 7000, 'ele-Reading', '1', 2, '0', '0', '0', CURRENT_TIMESTAMP),
        (7003, 'AI文档', 'document', NULL, '/ai/document', 7000, 'ele-Files', '1', 3, '0', '0', '0', CURRENT_TIMESTAMP),
        (7004, 'AI配置', 'config', NULL, '/ai/config', 7000, 'ele-Setting', '0', 4, '0', '0', '0', CURRENT_TIMESTAMP),
        (7011, 'AI对话', NULL, 'ai_chat_add', NULL, 7001, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (7021, 'AI知识库对话', NULL, 'ai_rag_add', NULL, 7002, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (7031, 'AI会话查看', NULL, 'ai_session_view', NULL, 7001, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (7032, 'AI会话新增', NULL, 'ai_session_add', NULL, 7001, NULL, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (7033, 'AI会话编辑', NULL, 'ai_session_edit', NULL, 7001, NULL, '0', 4, '1', '0', '0', CURRENT_TIMESTAMP),
        (7034, 'AI会话删除', NULL, 'ai_session_del', NULL, 7001, NULL, '0', 5, '1', '0', '0', CURRENT_TIMESTAMP),
        (7041, 'AI消息查看', NULL, 'ai_message_view', NULL, 7001, NULL, '0', 6, '1', '0', '0', CURRENT_TIMESTAMP),
        (7042, 'AI消息新增', NULL, 'ai_message_add', NULL, 7001, NULL, '0', 7, '1', '0', '0', CURRENT_TIMESTAMP),
        (7043, 'AI消息删除', NULL, 'ai_message_del', NULL, 7001, NULL, '0', 8, '1', '0', '0', CURRENT_TIMESTAMP),
        (7051, 'AI文档查看', NULL, 'ai_document_view', NULL, 7003, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (7052, 'AI文档新增', NULL, 'ai_document_add', NULL, 7003, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP),
        (7053, 'AI文档删除', NULL, 'ai_document_del', NULL, 7003, NULL, '0', 3, '1', '0', '0', CURRENT_TIMESTAMP),
        (7061, 'AI配置查看', NULL, 'ai_config_view', NULL, 7004, NULL, '0', 1, '1', '0', '0', CURRENT_TIMESTAMP),
        (7062, 'AI配置编辑', NULL, 'ai_config_edit', NULL, 7004, NULL, '0', 2, '1', '0', '0', CURRENT_TIMESTAMP);

    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_ai_menu_expected_20260926 expected ON expected.id = existing.id
    WHERE NOT (CAST(existing.path AS BINARY) <=> CAST(expected.path AS BINARY))
       OR NOT (CAST(existing.permission AS BINARY) <=> CAST(expected.permission AS BINARY))
       OR NOT (existing.parent_id <=> expected.parent_id)
       OR NOT (CAST(existing.type AS BINARY) <=> CAST(expected.type AS BINARY));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('AI menu conflict at id ', conflict_id,
                                      '; reconcile custom menu IDs before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    SELECT MIN(existing.id) INTO conflict_id
    FROM sys_menu existing
    JOIN bixi_ai_menu_expected_20260926 expected ON existing.id <> expected.id
      AND ((expected.path IS NOT NULL AND CAST(existing.path AS BINARY) = CAST(expected.path AS BINARY))
        OR (expected.permission IS NOT NULL AND CAST(existing.permission AS BINARY) = CAST(expected.permission AS BINARY)));
    IF conflict_id IS NOT NULL THEN
        SET conflict_message = CONCAT('AI menu identity already used at id ', conflict_id,
                                      '; reconcile custom permissions before retrying');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = conflict_message;
    END IF;

    INSERT INTO sys_menu
        (id, name, en_name, permission, path, parent_id, icon, visible, sn, type, keep_alive, embedded, create_time)
    SELECT expected.id, expected.name, expected.en_name, expected.permission, expected.path,
           expected.parent_id, expected.icon, expected.visible, expected.sn, expected.type,
           expected.keep_alive, expected.embedded, expected.create_time
    FROM bixi_ai_menu_expected_20260926 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.id = expected.id);

    INSERT INTO sys_role_menu (role_id, menu_id, create_time)
    SELECT 1, expected.id, CURRENT_TIMESTAMP
    FROM bixi_ai_menu_expected_20260926 expected
    WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu existing
                      WHERE existing.role_id = 1 AND existing.menu_id = expected.id);

    DROP TEMPORARY TABLE bixi_ai_menu_expected_20260926;
    COMMIT;
END$$
DELIMITER ;
CALL bixi_migrate_ai_menus_20260926();
DROP PROCEDURE bixi_migrate_ai_menus_20260926;
