-- Scope quarantine evidence to its receiving owner. Historical rows cannot be attributed safely,
-- so they are preserved under the non-routable "legacy" owner for direct forensic inspection.
-- Stop reliable consumers while this migration runs.
DROP PROCEDURE IF EXISTS migrate_reliable_quarantine_owner;
DELIMITER $$
CREATE PROCEDURE migrate_reliable_quarantine_owner()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing reliable_quarantine; apply the base reliable migration first';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine'
                AND column_name = 'target_owner')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine'
                AND column_name = 'target_owner' AND column_type = 'varchar(64)'
                AND is_nullable = 'NO' AND character_set_name = 'ascii'
                AND collation_name = 'ascii_bin') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid reliable_quarantine.target_owner definition';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine'
                AND index_name = 'PRIMARY'
            GROUP BY index_name HAVING COUNT(*) = 1 AND MAX(seq_in_index) = 1
                AND MAX(column_name) = 'evidence_id' AND MAX(sub_part) IS NULL)
        AND NOT EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine'
                AND index_name = 'PRIMARY'
            GROUP BY index_name HAVING COUNT(*) = 2 AND MAX(seq_in_index) = 2
                AND GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'target_owner,evidence_id'
                AND MAX(sub_part) IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid reliable_quarantine primary key';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine'
                AND column_name = 'target_owner') THEN
        ALTER TABLE reliable_quarantine
            ADD COLUMN target_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
                NOT NULL DEFAULT 'legacy' FIRST;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'reliable_quarantine'
                AND index_name = 'PRIMARY'
            GROUP BY index_name HAVING COUNT(*) = 1 AND MAX(seq_in_index) = 1
                AND MAX(column_name) = 'evidence_id' AND MAX(sub_part) IS NULL) THEN
        ALTER TABLE reliable_quarantine DROP PRIMARY KEY,
            ADD PRIMARY KEY (target_owner, evidence_id);
    END IF;

    ALTER TABLE reliable_quarantine
        MODIFY COLUMN target_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;
END$$
DELIMITER ;
CALL migrate_reliable_quarantine_owner();
DROP PROCEDURE migrate_reliable_quarantine_owner;
