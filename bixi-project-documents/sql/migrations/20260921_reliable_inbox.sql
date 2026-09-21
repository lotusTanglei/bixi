-- Explicitly apply to each target database before wiring its executor. All timestamps use UTC.
CREATE TABLE IF NOT EXISTS reliable_inbox (
    target_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version INT NOT NULL,
    -- Application DurableMessage validation preserves the canonical arbitrary-precision text contract.
    payload_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'RECEIVED',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until DATETIME(6) NULL,
    last_error VARCHAR(256) NULL,
    received_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (target_owner, event_id),
    KEY idx_reliable_inbox_due (target_owner, status, next_attempt_at, received_at),
    KEY idx_reliable_inbox_lease (target_owner, status, lease_until),
    CONSTRAINT chk_reliable_inbox_status CHECK (status IN ('RECEIVED', 'IN_FLIGHT', 'PROCESSED', 'IGNORED', 'FAILED')),
    CONSTRAINT chk_reliable_inbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_reliable_inbox_schema CHECK (schema_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
