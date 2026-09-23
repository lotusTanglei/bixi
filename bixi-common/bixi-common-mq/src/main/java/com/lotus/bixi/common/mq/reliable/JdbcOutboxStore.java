package com.lotus.bixi.common.mq.reliable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Explicitly constructed JDBC outbox; enqueue joins the caller's local transaction.
 * The transaction manager must manage this exact DataSource, including any routing wrapper.
 * No component scanning, scheduler, or transport is installed by this class.
 */
public class JdbcOutboxStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate mandatory;
    private final TransactionTemplate independent;
    private final ReliableDeliveryProperties properties;

    public JdbcOutboxStore(DataSource dataSource, PlatformTransactionManager transactionManager,
            ReliableDeliveryProperties properties) {
        Objects.requireNonNull(dataSource, "DataSource is required");
        if (!(transactionManager instanceof DataSourceTransactionManager local)
                || local.getDataSource() != dataSource) {
            throw new IllegalArgumentException("A local JDBC transaction manager for the same DataSource is required");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = Objects.requireNonNull(properties, "Delivery properties are required");
        this.mandatory = new TransactionTemplate(transactionManager);
        this.mandatory.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independent.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Persist the exact message in the existing business transaction. A duplicate must match
     * event ID, deduplication key, routing, schema, canonical payload and aggregate metadata.
     * Conflicts mark the whole transaction rollback-only even when caught by the caller.
     */
    public void enqueue(DurableMessage message, String dedupKey, String aggregateKey, Long aggregateSequence) {
        mandatory.executeWithoutResult(status -> {
            Objects.requireNonNull(message, "Message is required");
            requireKey(dedupKey, "dedupKey");
            if (aggregateKey != null) {
                requireKey(aggregateKey, "aggregateKey");
            }
            if ((aggregateKey == null) != (aggregateSequence == null)
                    || (aggregateSequence != null && aggregateSequence < 0)) {
                throw new IllegalArgumentException("Aggregate key and nonnegative sequence must be supplied together");
            }
            // A no-op upsert takes the unique-key lock without aborting this business transaction.
            // The subsequent FOR UPDATE is a current read, even under MySQL REPEATABLE READ.
            jdbc.update("""
                    INSERT INTO reliable_outbox
                        (source_owner, event_id, dedup_key, target_owner, type, schema_version,
                         payload_json, payload_hash, aggregate_key, aggregate_sequence, next_attempt_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE event_id = reliable_outbox.event_id
                    """, message.sourceOwner(), message.eventId(), dedupKey, message.targetOwner(), message.type(),
                    message.schemaVersion(), message.payloadJson(), message.payloadHash(), aggregateKey, aggregateSequence);
            // The upsert has already serialized an insert or either unique-key conflict. Read
            // only by the primary key: a missing row therefore means the dedup key belongs to a
            // different event. Combining both indexes with OR here deadlocks independent writers.
            List<Boolean> matches = jdbc.query("""
                    SELECT * FROM reliable_outbox
                    WHERE source_owner = ? AND event_id = ? FOR UPDATE
                    """, (row, index) -> message.equals(readMessage(row))
                            && dedupKey.equals(row.getString("dedup_key"))
                            && Objects.equals(aggregateKey, row.getString("aggregate_key"))
                            && Objects.equals(aggregateSequence, row.getObject("aggregate_sequence", Long.class)),
                    message.sourceOwner(), message.eventId());
            if (matches.size() != 1 || !matches.get(0)) {
                throw new OutboxConflictException();
            }
        });
    }

    /** Claim no more than immediately available sender capacity (at most twenty). */
    public List<Lease> claim(String sourceOwner, int capacity) {
        DurableMessage.requireOwner(sourceOwner);
        if (capacity < 1 || capacity > 20) {
            throw new IllegalArgumentException("Claim capacity must be between 1 and 20");
        }
        return independent.execute(status -> {
            // Separate index-ordered reads avoid the filesort of a mixed-status OR query,
            // which can lock the entire due set before applying LIMIT. READ COMMITTED also
            // avoids empty-range gap locks blocking a second worker's state transition.
            List<Candidate> candidates = new ArrayList<>(jdbc.query("""
                    SELECT * FROM reliable_outbox FORCE INDEX (idx_reliable_outbox_lease)
                    WHERE source_owner = ? AND status = 'IN_FLIGHT' AND lease_until <= UTC_TIMESTAMP(6)
                    ORDER BY lease_until LIMIT ? FOR UPDATE SKIP LOCKED
                    """, (row, index) -> new Candidate(readMessage(row), row.getInt("attempts")), sourceOwner, capacity));
            if (candidates.size() < capacity) {
                candidates.addAll(jdbc.query("""
                        SELECT * FROM reliable_outbox FORCE INDEX (idx_reliable_outbox_due)
                        WHERE source_owner = ? AND status = 'PENDING' AND next_attempt_at <= UTC_TIMESTAMP(6)
                        ORDER BY next_attempt_at, created_at LIMIT ? FOR UPDATE SKIP LOCKED
                        """, (row, index) -> new Candidate(readMessage(row), row.getInt("attempts")),
                        sourceOwner, capacity - candidates.size()));
            }
            List<Lease> leases = new ArrayList<>();
            for (Candidate candidate : candidates) {
                if (candidate.attempts() >= properties.maxAttempts()) {
                    // A worker may have died on its final lease, without ever recording a failure.
                    jdbc.update("""
                            UPDATE reliable_outbox SET status = 'FAILED', lease_token = NULL, lease_until = NULL,
                                last_error = 'Delivery attempt limit reached'
                            WHERE source_owner = ? AND event_id = ?
                            """, sourceOwner, candidate.message().eventId());
                    continue;
                }
                String token = UUID.randomUUID().toString();
                jdbc.update("""
                        UPDATE reliable_outbox SET status = 'IN_FLIGHT', attempts = attempts + 1,
                            lease_token = ?, lease_until = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6))
                        WHERE source_owner = ? AND event_id = ?
                        """, token, properties.leaseDuration().toNanos() / 1000, sourceOwner, candidate.message().eventId());
                leases.add(new Lease(candidate.message(), token, candidate.attempts() + 1));
            }
            return List.copyOf(leases);
        });
    }

    /** Read bounded operator metadata without exposing payload contents to management APIs. */
    public List<AdminSnapshot> list(String sourceOwner, String status, int limit) {
        DurableMessage.requireOwner(sourceOwner);
        requireStatus(status);
        requireLimit(limit);
        return independent.execute(transaction -> {
            String sql = status == null
                    ? "SELECT * FROM reliable_outbox WHERE source_owner = ? ORDER BY created_at DESC LIMIT ?"
                    : "SELECT * FROM reliable_outbox WHERE source_owner = ? AND status = ? "
                            + "ORDER BY created_at DESC LIMIT ?";
            Object[] args = status == null ? new Object[] {sourceOwner, limit}
                    : new Object[] {sourceOwner, status, limit};
            return List.copyOf(jdbc.query(sql, (row, index) -> readAdminSnapshot(row), args));
        });
    }

    /** Move one failed event back to the normal due queue; the status predicate is the CAS fence. */
    public boolean retry(String sourceOwner, String eventId) {
        DurableMessage.requireOwner(sourceOwner);
        requireEventId(eventId);
        return Boolean.TRUE.equals(independent.execute(transaction -> jdbc.update("""
                UPDATE reliable_outbox SET status = 'PENDING', next_attempt_at = UTC_TIMESTAMP(6),
                    lease_token = NULL, lease_until = NULL, last_error = NULL
                WHERE source_owner = ? AND event_id = ? AND status = 'FAILED'
                """, sourceOwner, eventId) == 1));
    }

    /** False means another worker owns this message, or its state already changed. */
    public boolean markDelivered(Lease lease) {
        Objects.requireNonNull(lease, "Lease is required");
        return Boolean.TRUE.equals(independent.execute(status -> jdbc.update("""
                UPDATE reliable_outbox SET status = 'DELIVERED', delivered_at = UTC_TIMESTAMP(6),
                    lease_token = NULL, lease_until = NULL, last_error = NULL
                WHERE source_owner = ? AND event_id = ? AND status = 'IN_FLIGHT' AND lease_token = ?
                """, lease.message().sourceOwner(), lease.message().eventId(), lease.leaseToken()) == 1));
    }

    /** Persist retry responsibility. Only the error's bounded class name is stored, never its message. */
    public boolean markFailed(Lease lease, Throwable error) {
        Objects.requireNonNull(lease, "Lease is required");
        String safeError = Objects.requireNonNull(error, "Failure is required").getClass().getName();
        safeError = safeError.substring(0, Math.min(safeError.length(), 256));
        String finalError = safeError;
        long delayMicros = properties.retryDelay(lease.attempt()).toNanos() / 1000;
        return Boolean.TRUE.equals(independent.execute(status -> jdbc.update("""
                UPDATE reliable_outbox SET status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6)),
                    lease_token = NULL, lease_until = NULL, last_error = ?
                WHERE source_owner = ? AND event_id = ? AND status = 'IN_FLIGHT' AND lease_token = ?
                """, properties.maxAttempts(), delayMicros, finalError,
                lease.message().sourceOwner(), lease.message().eventId(), lease.leaseToken()) == 1));
    }

    private static DurableMessage readMessage(ResultSet row) throws SQLException {
        return new DurableMessage(row.getString("source_owner"), row.getString("target_owner"), row.getString("event_id"),
                row.getString("type"), row.getInt("schema_version"), row.getString("payload_json"), row.getString("payload_hash"));
    }

    private static AdminSnapshot readAdminSnapshot(ResultSet row) throws SQLException {
        return new AdminSnapshot(readMessage(row), row.getString("dedup_key"), row.getString("aggregate_key"),
                row.getObject("aggregate_sequence", Long.class), row.getString("status"), row.getInt("attempts"),
                instant(row, "next_attempt_at"), instant(row, "lease_until"), row.getString("last_error"),
                instant(row, "created_at"), instant(row, "delivered_at"));
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void requireStatus(String status) {
        if (status != null && !status.matches("PENDING|IN_FLIGHT|DELIVERED|FAILED")) {
            throw new IllegalArgumentException("Invalid outbox status");
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Limit must be between 1 and 200");
        }
    }

    private static void requireEventId(String eventId) {
        if (eventId == null || !UUID.fromString(eventId).toString().equals(eventId)) {
            throw new IllegalArgumentException("eventId must be a canonical lowercase UUID");
        }
    }

    private static void requireKey(String key, String name) {
        if (key == null || !key.matches("[!-~]{1,191}")) {
            throw new IllegalArgumentException(name + " must be 1 to 191 printable ASCII characters without spaces");
        }
    }

    public record Lease(DurableMessage message, String leaseToken, int attempt) { }

    public record AdminSnapshot(DurableMessage message, String dedupKey, String aggregateKey,
            Long aggregateSequence, String status, int attempts, java.time.Instant nextAttemptAt,
            java.time.Instant leaseUntil, String lastError, java.time.Instant createdAt,
            java.time.Instant deliveredAt) { }

    private record Candidate(DurableMessage message, int attempts) { }
}
