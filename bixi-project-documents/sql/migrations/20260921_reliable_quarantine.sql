-- Apply to each target database before enabling the Rabbit inbox listener.
CREATE TABLE IF NOT EXISTS reliable_quarantine (
    evidence_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    body_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    reason VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    quarantined_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (evidence_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
