-- Apply after the reliable outbox/inbox/quarantine migrations and before enabling recovery APIs.
CREATE TABLE IF NOT EXISTS wf_recovery_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    evidence_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    changed TINYINT(1) NOT NULL,
    reason VARCHAR(256) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (id),
    KEY idx_wf_recovery_audit_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='可靠投递人工恢复审计';
