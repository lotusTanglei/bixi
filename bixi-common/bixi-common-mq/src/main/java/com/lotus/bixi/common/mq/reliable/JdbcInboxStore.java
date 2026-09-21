package com.lotus.bixi.common.mq.reliable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Explicit local JDBC inbox; reception, leasing and handler transactions have distinct commit boundaries. */
public final class JdbcInboxStore {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;
    private final ReliableDeliveryProperties properties;

    public JdbcInboxStore(DataSource dataSource, PlatformTransactionManager transactionManager,
            ReliableDeliveryProperties properties) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource is required");
        if (!(transactionManager instanceof DataSourceTransactionManager local)
                || local.getDataSource() != dataSource) {
            throw new IllegalArgumentException("A local JDBC transaction manager for the same DataSource is required");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = Objects.requireNonNull(properties, "Delivery properties are required");
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independent.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Persist takeover without declaring business success. Ingress must validate its registry first. */
    public Snapshot accept(DurableMessage message) {
        Objects.requireNonNull(message, "Message is required");
        return independent.execute(transaction -> {
            jdbc.update("""
                    INSERT INTO reliable_inbox
                        (target_owner, event_id, source_owner, type, schema_version, payload_json, payload_hash,
                         next_attempt_at, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE event_id = reliable_inbox.event_id
                    """, message.targetOwner(), message.eventId(), message.sourceOwner(), message.type(),
                    message.schemaVersion(), message.payloadJson(), message.payloadHash());
            // The upsert and current read see a committed duplicate even with an old outer RR snapshot.
            Snapshot saved = jdbc.queryForObject("""
                    SELECT * FROM reliable_inbox WHERE target_owner = ? AND event_id = ? FOR UPDATE
                    """, (row, index) -> readSnapshot(row), message.targetOwner(), message.eventId());
            if (!message.equals(saved.message())) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.CONFLICT,
                        "Inbox identity conflicts with immutable message content");
            }
            return saved;
        });
    }

    public Snapshot find(String targetOwner, String eventId) {
        requireIdentity(targetOwner, eventId);
        return independent.execute(transaction -> {
            List<Snapshot> rows = jdbc.query("SELECT * FROM reliable_inbox WHERE target_owner = ? AND event_id = ?",
                    (row, index) -> readSnapshot(row), targetOwner, eventId);
            return rows.isEmpty() ? null : rows.get(0);
        });
    }

    /** Claim at most immediately usable capacity. Independent workers skip held processing locks. */
    public List<Lease> claim(String targetOwner, int capacity) {
        return claimBatch(targetOwner, capacity).leases();
    }

    /** Include terminalized candidates so recovery can distinguish progress from an empty scan. */
    ClaimBatch claimBatch(String targetOwner, int capacity) {
        DurableMessage.requireOwner(targetOwner);
        if (capacity < 1 || capacity > 20) {
            throw new IllegalArgumentException("Claim capacity must be between 1 and 20");
        }
        return independent.execute(transaction -> {
            List<Snapshot> candidates = new ArrayList<>(jdbc.query("""
                    SELECT * FROM reliable_inbox FORCE INDEX (idx_reliable_inbox_lease)
                    WHERE target_owner = ? AND status = 'IN_FLIGHT' AND lease_until <= UTC_TIMESTAMP(6)
                    ORDER BY lease_until LIMIT ? FOR UPDATE SKIP LOCKED
                    """, (row, index) -> readSnapshot(row), targetOwner, capacity));
            if (candidates.size() < capacity) {
                candidates.addAll(jdbc.query("""
                        SELECT * FROM reliable_inbox FORCE INDEX (idx_reliable_inbox_due)
                        WHERE target_owner = ? AND status = 'RECEIVED' AND next_attempt_at <= UTC_TIMESTAMP(6)
                        ORDER BY next_attempt_at, received_at LIMIT ? FOR UPDATE SKIP LOCKED
                        """, (row, index) -> readSnapshot(row), targetOwner, capacity - candidates.size()));
            }
            List<Lease> leases = new ArrayList<>();
            for (Snapshot candidate : candidates) {
                Lease lease = lease(candidate);
                if (lease != null) {
                    leases.add(lease);
                }
            }
            return new ClaimBatch(List.copyOf(leases), candidates.size());
        });
    }

    /** Targeted reception uses the exact same persisted lease rules as recovery scanning. */
    public Lease claim(String targetOwner, String eventId) {
        requireIdentity(targetOwner, eventId);
        return independent.execute(transaction -> {
            List<Snapshot> rows = jdbc.query("""
                    SELECT * FROM reliable_inbox WHERE target_owner = ? AND event_id = ? AND
                        ((status = 'RECEIVED' AND next_attempt_at <= UTC_TIMESTAMP(6)) OR
                         (status = 'IN_FLIGHT' AND lease_until <= UTC_TIMESTAMP(6)))
                    FOR UPDATE SKIP LOCKED
                    """, (row, index) -> readSnapshot(row), targetOwner, eventId);
            return rows.isEmpty() ? null : lease(rows.get(0));
        });
    }

    private Lease lease(Snapshot candidate) {
        DurableMessage message = candidate.message();
        if (candidate.attempts() >= properties.maxAttempts()) {
            jdbc.update("""
                    UPDATE reliable_inbox SET status = 'FAILED', lease_token = NULL, lease_until = NULL,
                        last_error = 'Delivery attempt limit reached'
                    WHERE target_owner = ? AND event_id = ?
                    """, message.targetOwner(), message.eventId());
            return null;
        }
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE reliable_inbox SET status = 'IN_FLIGHT', attempts = attempts + 1,
                    lease_token = ?, lease_until = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6))
                WHERE target_owner = ? AND event_id = ?
                """, token, properties.leaseDuration().toNanos() / 1000, message.targetOwner(), message.eventId());
        return new Lease(message, token, candidate.attempts() + 1);
    }

    /**
     * Lock and validate the live lease BEFORE entering the handler. The row stays locked through
     * business work, successor outbox writes and the committed terminal inbox result. A lease
     * expiring during this transaction cannot be stolen; no external work belongs in this handler.
     */
    DurableMessageHandler.Result process(Lease lease, DurableMessageHandler handler) {
        requireOutsideTransaction();
        Objects.requireNonNull(lease, "Lease is required");
        Objects.requireNonNull(handler, "Handler is required");
        return independent.execute(transaction -> {
            List<Boolean> ownership = jdbc.query("""
                    SELECT * FROM reliable_inbox
                    WHERE target_owner = ? AND event_id = ? FOR UPDATE
                    """, (row, index) -> "IN_FLIGHT".equals(row.getString("status"))
                            && lease.leaseToken().equals(row.getString("lease_token"))
                            && lease.message().equals(readMessage(row)),
                    lease.message().targetOwner(), lease.message().eventId());
            if (ownership.size() != 1 || !ownership.get(0)) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                        "Inbox lease is no longer valid for execution");
            }
            // MySQL UTC_TIMESTAMP is fixed at statement start. The locking SELECT may have
            // waited until after expiry; a new statement now observes time AFTER lock acquisition.
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT lease_until > UTC_TIMESTAMP(6) FROM reliable_inbox
                    WHERE target_owner = ? AND event_id = ?
                    """, Boolean.class, lease.message().targetOwner(), lease.message().eventId()))) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                        "Inbox lease expired before handler execution");
            }
            DurableMessageHandler.Result result = Objects.requireNonNull(handler.handle(lease.message()),
                    "Handler must return a committed processing outcome");
            int changed = jdbc.update("""
                    UPDATE reliable_inbox SET status = ?, processed_at = UTC_TIMESTAMP(6),
                        lease_token = NULL, lease_until = NULL, last_error = NULL
                    WHERE target_owner = ? AND event_id = ? AND status = 'IN_FLIGHT' AND lease_token = ?
                    """, result.name(), lease.message().targetOwner(), lease.message().eventId(), lease.leaseToken());
            if (changed != 1) {
                throw new IllegalStateException("Inbox lease changed during its locked processing transaction");
            }
            return result;
        });
    }

    /** Called only after the failed handler transaction has rolled back and released its resources. */
    public boolean markFailed(Lease lease, Throwable failure) {
        Objects.requireNonNull(lease, "Lease is required");
        Objects.requireNonNull(failure, "Failure is required");
        boolean permanent = failure instanceof InboxDeliveryException delivery
                && delivery.kind() != InboxDeliveryException.Kind.RETRYABLE;
        String name = failure.getClass().getName();
        String safeError = name.substring(0, Math.min(name.length(), 256));
        long delayMicros = properties.retryDelay(lease.attempt()).toNanos() / 1000;
        return Boolean.TRUE.equals(independent.execute(transaction -> jdbc.update("""
                UPDATE reliable_inbox SET status = CASE WHEN ? OR attempts >= ? THEN 'FAILED' ELSE 'RECEIVED' END,
                    next_attempt_at = TIMESTAMPADD(MICROSECOND, ?, UTC_TIMESTAMP(6)),
                    lease_token = NULL, lease_until = NULL, last_error = ?
                WHERE target_owner = ? AND event_id = ? AND status = 'IN_FLIGHT' AND lease_token = ?
                """, permanent, properties.maxAttempts(), delayMicros, safeError,
                lease.message().targetOwner(), lease.message().eventId(), lease.leaseToken()) == 1));
    }

    void requireOutsideTransaction() {
        InboxExecutor.requireNoAmbientTransaction();
        if (TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Inbox execution cannot reuse a bound source transaction resource");
        }
    }

    private static Snapshot readSnapshot(ResultSet row) throws SQLException {
        return new Snapshot(readMessage(row), State.valueOf(row.getString("status")), row.getInt("attempts"));
    }

    private static DurableMessage readMessage(ResultSet row) throws SQLException {
        return new DurableMessage(row.getString("source_owner"), row.getString("target_owner"), row.getString("event_id"),
                row.getString("type"), row.getInt("schema_version"), row.getString("payload_json"), row.getString("payload_hash"));
    }

    private static void requireIdentity(String owner, String eventId) {
        DurableMessage.requireOwner(owner);
        if (eventId == null || !UUID.fromString(eventId).toString().equals(eventId)) {
            throw new IllegalArgumentException("eventId must be a canonical lowercase UUID");
        }
    }

    record ClaimBatch(List<Lease> leases, int scanned) { }

    public enum State { RECEIVED, IN_FLIGHT, PROCESSED, IGNORED, FAILED }

    public record Snapshot(DurableMessage message, State state, int attempts) { }

    public record Lease(DurableMessage message, String leaseToken, int attempt) { }
}
