-- Apply to each source owner's database before explicitly wiring an outbox store.
-- All timestamps are UTC. No scheduler or runtime is enabled by this migration.
CREATE TABLE IF NOT EXISTS reliable_outbox (
    source_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    dedup_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_owner VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    type VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version INT NOT NULL,
    -- DurableMessage validates JSON; LONGTEXT preserves arbitrary-precision numbers and nesting.
    payload_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_key VARCHAR(191) CHARACTER SET ascii COLLATE ascii_bin NULL,
    aggregate_sequence BIGINT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    lease_token CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until DATETIME(6) NULL,
    last_error VARCHAR(256) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    delivered_at DATETIME(6) NULL,
    PRIMARY KEY (source_owner, event_id),
    UNIQUE KEY uk_reliable_outbox_dedup (source_owner, dedup_key),
    KEY idx_reliable_outbox_due (source_owner, status, next_attempt_at, created_at),
    KEY idx_reliable_outbox_lease (source_owner, status, lease_until),
    CONSTRAINT chk_reliable_outbox_status CHECK (status IN ('PENDING', 'IN_FLIGHT', 'DELIVERED', 'FAILED')),
    CONSTRAINT chk_reliable_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_reliable_outbox_schema CHECK (schema_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
