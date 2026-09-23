-- Shared request idempotency state. Business modules keep their side effects in the same local transaction.
CREATE TABLE IF NOT EXISTS reliable_idempotency (
    tenant_id BIGINT NOT NULL,
    scope VARCHAR(96) NOT NULL,
    idempotency_key VARCHAR(191) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_code INT NULL,
    response_body LONGTEXT NULL,
    last_error VARCHAR(256) NULL,
    attempts INT NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, scope, idempotency_key),
    KEY idx_reliable_idempotency_expiry (status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用请求幂等状态';
