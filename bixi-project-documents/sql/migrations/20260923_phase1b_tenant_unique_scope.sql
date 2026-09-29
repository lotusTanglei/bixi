-- Make natural identifiers unique inside a tenant instead of globally.
-- Run after 20260921_phase1b_tenant_backfill.sql with application writes paused.
-- The migration is deliberately fail-closed: duplicate tenant-local values must
-- be resolved by an operator before constraints are changed.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS bixi_migrate_tenant_unique_scope_20260923;
DELIMITER $$
CREATE PROCEDURE bixi_migrate_tenant_unique_scope_20260923()
BEGIN
    DECLARE duplicate_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;

    START TRANSACTION;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT tenant_id, username FROM sys_user
        WHERE username IS NOT NULL GROUP BY tenant_id, username HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate tenant-local usernames require manual resolution';
    END IF;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT tenant_id, code FROM sys_role
        WHERE code IS NOT NULL GROUP BY tenant_id, code HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate tenant-local role codes require manual resolution';
    END IF;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT tenant_id, type FROM sys_dict
        WHERE type IS NOT NULL GROUP BY tenant_id, type HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate tenant-local dictionary types require manual resolution';
    END IF;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT tenant_id, permission FROM sys_menu
        WHERE permission IS NOT NULL AND permission <> ''
        GROUP BY tenant_id, permission HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate tenant-local menu permissions require manual resolution';
    END IF;

    SELECT COUNT(*) INTO duplicate_count FROM (
        SELECT tenant_id, code FROM sys_dept
        WHERE code IS NOT NULL GROUP BY tenant_id, code HAVING COUNT(*) > 1
    ) duplicates;
    IF duplicate_count > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate tenant-local department codes require manual resolution';
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_username';
    IF index_count > 0 THEN
        ALTER TABLE sys_user DROP INDEX uk_username;
    END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_phone';
    IF index_count > 0 THEN
        ALTER TABLE sys_user DROP INDEX uk_phone;
    END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_email';
    IF index_count > 0 THEN
        ALTER TABLE sys_user DROP INDEX uk_email;
    END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_user_tenant_username';
    IF index_count = 0 THEN
        ALTER TABLE sys_user ADD CONSTRAINT uk_user_tenant_username UNIQUE (tenant_id, username);
    END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_user_tenant_phone';
    IF index_count = 0 THEN
        ALTER TABLE sys_user ADD CONSTRAINT uk_user_tenant_phone UNIQUE (tenant_id, phone);
    END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_user_tenant_email';
    IF index_count = 0 THEN
        ALTER TABLE sys_user ADD CONSTRAINT uk_user_tenant_email UNIQUE (tenant_id, email);
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND index_name = 'uk_role_code';
    IF index_count > 0 THEN ALTER TABLE sys_role DROP INDEX uk_role_code; END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND index_name = 'uk_role_tenant_code';
    IF index_count = 0 THEN
        ALTER TABLE sys_role ADD CONSTRAINT uk_role_tenant_code UNIQUE (tenant_id, code);
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict' AND index_name = 'uk_dict_type';
    IF index_count > 0 THEN ALTER TABLE sys_dict DROP INDEX uk_dict_type; END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_dict' AND index_name = 'uk_dict_tenant_type';
    IF index_count = 0 THEN
        ALTER TABLE sys_dict ADD CONSTRAINT uk_dict_tenant_type UNIQUE (tenant_id, type);
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND index_name = 'uk_menu_perm';
    IF index_count > 0 THEN ALTER TABLE sys_menu DROP INDEX uk_menu_perm; END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_menu' AND index_name = 'uk_menu_tenant_perm';
    IF index_count = 0 THEN
        ALTER TABLE sys_menu ADD CONSTRAINT uk_menu_tenant_perm UNIQUE (tenant_id, permission);
    END IF;

    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_dept' AND index_name = 'uk_dept_code';
    IF index_count > 0 THEN ALTER TABLE sys_dept DROP INDEX uk_dept_code; END IF;
    SELECT COUNT(*) INTO index_count FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_dept' AND index_name = 'uk_dept_tenant_code';
    IF index_count = 0 THEN
        ALTER TABLE sys_dept ADD CONSTRAINT uk_dept_tenant_code UNIQUE (tenant_id, code);
    END IF;

    COMMIT;
END$$
DELIMITER ;
CALL bixi_migrate_tenant_unique_scope_20260923();
DROP PROCEDURE bixi_migrate_tenant_unique_scope_20260923;
