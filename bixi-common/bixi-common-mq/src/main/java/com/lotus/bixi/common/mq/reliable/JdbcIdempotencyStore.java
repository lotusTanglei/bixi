package com.lotus.bixi.common.mq.reliable;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

/**
 * Persistent request idempotency state shared by HTTP, jobs and message handlers.
 * The caller owns the business side effect; this store only fences duplicate execution
 * and keeps a bounded, replayable result.
 */
public final class JdbcIdempotencyStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate independent;

    public JdbcIdempotencyStore(DataSource dataSource, DataSourceTransactionManager transactionManager) {
        Objects.requireNonNull(dataSource, "DataSource is required");
        if (transactionManager == null || transactionManager.getDataSource() != dataSource) {
            throw new IllegalArgumentException("A transaction manager for the same DataSource is required");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(transactionManager);
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independent.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public Decision begin(Long tenantId, String scope, String key, String requestHash, Duration timeout) {
        requireIdentity(tenantId, scope, key, requestHash);
        long timeoutMicros = requireTimeout(timeout);
        return transaction.execute(status -> {
            boolean inserted = false;
            try {
                jdbc.update("""
                        INSERT INTO reliable_idempotency
                            (tenant_id, scope, idempotency_key, request_hash, status, attempts,
                             expires_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 'IN_PROGRESS', 1,
                                TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6)),
                                UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                        """, tenantId, scope, key, requestHash, timeoutMicros);
                inserted = true;
            }
            catch (DuplicateKeyException ignored) {
                // The current row is read and locked below. Do not interpret a duplicate insert
                // as success until its immutable request hash and state have been checked.
            }

            Row row = readForUpdate(tenantId, scope, key);
            if (!requestHash.equals(row.requestHash())) {
                return new Decision(State.CONFLICT, row.responseCode(), null, row.attempts());
            }
            if (inserted) {
                return new Decision(State.ACQUIRED, null, null, row.attempts());
            }
            if (row.state() == State.SUCCEEDED) {
                return new Decision(State.REPLAY, row.responseCode(), row.responseBody(), row.attempts());
            }
            if (row.state() == State.FAILED) {
                return new Decision(State.FAILED, row.responseCode(), row.responseBody(), row.attempts());
            }
            if (row.state() == State.IN_PROGRESS && row.expired()) {
                jdbc.update("""
                        UPDATE reliable_idempotency
                        SET status='IN_PROGRESS', attempts=attempts + 1,
                            expires_at=TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6)),
                            updated_at=UTC_TIMESTAMP(6), last_error=NULL
                        WHERE tenant_id=? AND scope=? AND idempotency_key=? AND status='IN_PROGRESS'
                        """, timeoutMicros, tenantId, scope, key);
                return new Decision(State.ACQUIRED, null, null, row.attempts() + 1);
            }
            return new Decision(State.IN_PROGRESS, null, null, row.attempts());
        });
    }

    public void complete(Long tenantId, String scope, String key, String requestHash,
                         int responseCode, String responseBody) {
        requireIdentity(tenantId, scope, key, requestHash);
        transaction.executeWithoutResult(status -> {
            Row row = readForUpdate(tenantId, scope, key);
            requireOwned(row, requestHash);
            if (row.state() != State.IN_PROGRESS) {
                throw new IllegalStateException("idempotency_not_in_progress");
            }
            jdbc.update("""
                    UPDATE reliable_idempotency
                    SET status='SUCCEEDED', response_code=?, response_body=?,
                        last_error=NULL, updated_at=UTC_TIMESTAMP(6)
                    WHERE tenant_id=? AND scope=? AND idempotency_key=? AND status='IN_PROGRESS'
                    """, responseCode, responseBody, tenantId, scope, key);
        });
    }

    public void fail(Long tenantId, String scope, String key, String requestHash,
                     int responseCode, Throwable failure) {
        requireIdentity(tenantId, scope, key, requestHash);
        String error = Objects.requireNonNull(failure, "Failure is required").getClass().getName();
        error = error.substring(0, Math.min(error.length(), 256));
        String safeError = error;
        transaction.executeWithoutResult(status -> {
            Row row = readForUpdate(tenantId, scope, key);
            requireOwned(row, requestHash);
            if (row.state() != State.IN_PROGRESS) {
                throw new IllegalStateException("idempotency_not_in_progress");
            }
            jdbc.update("""
                    UPDATE reliable_idempotency
                    SET status='FAILED', response_code=?, response_body=NULL,
                        last_error=?, updated_at=UTC_TIMESTAMP(6)
                    WHERE tenant_id=? AND scope=? AND idempotency_key=? AND status='IN_PROGRESS'
                    """, responseCode, safeError, tenantId, scope, key);
        });
    }

    public Decision find(Long tenantId, String scope, String key) {
        requireIdentity(tenantId, scope, key, "0".repeat(64));
        return independent.execute(status -> {
            java.util.List<Row> rows = jdbc.query("""
                    SELECT request_hash, status, response_code, response_body, attempts, expires_at,
                           expires_at <= UTC_TIMESTAMP(6) AS expired
                    FROM reliable_idempotency
                    WHERE tenant_id=? AND scope=? AND idempotency_key=?
                    """, (result, index) -> readRow(result), tenantId, scope, key);
            if (rows.isEmpty()) return null;
            Row row = rows.get(0);
            return new Decision(row.state(), row.responseCode(), row.responseBody(), row.attempts());
        });
    }

    private Row readForUpdate(Long tenantId, String scope, String key) {
        return jdbc.queryForObject("""
                SELECT request_hash, status, response_code, response_body, attempts, expires_at,
                       expires_at <= UTC_TIMESTAMP(6) AS expired
                FROM reliable_idempotency
                WHERE tenant_id=? AND scope=? AND idempotency_key=? FOR UPDATE
                """, (result, index) -> readRow(result), tenantId, scope, key);
    }

    private Row readRow(ResultSet result) throws SQLException {
        java.sql.Timestamp expires = result.getTimestamp("expires_at");
        return new Row(result.getString("request_hash"), State.valueOf(result.getString("status")),
                (Integer) result.getObject("response_code"), result.getString("response_body"),
                result.getInt("attempts"), expires.toInstant(), result.getBoolean("expired"));
    }

    private static void requireOwned(Row row, String requestHash) {
        if (row == null || !requestHash.equals(row.requestHash())) {
            throw new IllegalArgumentException("idempotency_request_conflict");
        }
    }

    private static void requireIdentity(Long tenantId, String scope, String key, String hash) {
        if (tenantId == null || tenantId <= 0) throw new IllegalArgumentException("tenantId is required");
        if (scope == null || !scope.matches("[A-Za-z0-9_.:-]{1,96}")) {
            throw new IllegalArgumentException("scope is invalid");
        }
        if (key == null || !key.matches("[!-~]{1,191}")) {
            throw new IllegalArgumentException("idempotency key is invalid");
        }
        if (hash == null || !hash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("request hash is invalid");
        }
    }

    private static long requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("timeout must be between 1 microsecond and 24 hours");
        }
        return timeout.toNanos() / 1_000;
    }

    private record Row(String requestHash, State state, Integer responseCode, String responseBody,
                       int attempts, java.time.Instant expiresAt, boolean expired) { }

    public enum State { ACQUIRED, IN_PROGRESS, REPLAY, CONFLICT, SUCCEEDED, FAILED }

    public record Decision(State state, Integer responseCode, String responseBody, int attempts) { }
}
