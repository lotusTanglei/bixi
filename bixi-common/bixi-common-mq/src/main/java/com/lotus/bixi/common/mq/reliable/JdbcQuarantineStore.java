package com.lotus.bixi.common.mq.reliable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Records messages that the inbox cannot accept: invalid wire format, routing mismatch,
 * or schema that no handler recognises. Quarantined rows keep the original payload and
 * a bounded reason code so operators can diagnose and replay; they do not participate
 * in the normal inbox retry schedule.
 */
public class JdbcQuarantineStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate independent;

    public JdbcQuarantineStore(DataSource dataSource, PlatformTransactionManager transactionManager) {
        Objects.requireNonNull(dataSource, "DataSource is required");
        if (!(transactionManager instanceof DataSourceTransactionManager local)
                || local.getDataSource() != dataSource) {
            throw new IllegalArgumentException("A local JDBC transaction manager for the same DataSource is required");
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.independent = new TransactionTemplate(transactionManager);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.independent.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Persist the rejected message with a bounded reason. A duplicate evidence ID updates
     * the reason but never replaces the original payload, so the first observed wire bytes
     * remain available for diagnosis.
     */
    public void quarantine(String evidenceId, String bodyJson, String reason) {
        requireEvidence(evidenceId);
        requireReason(reason);
        String safeBody = bodyJson == null ? "" : bodyJson;
        independent.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO reliable_quarantine (evidence_id, body_json, reason, quarantined_at)
                    VALUES (?, ?, ?, UTC_TIMESTAMP(6))
                    ON DUPLICATE KEY UPDATE reason = VALUES(reason)
                    """, evidenceId, safeBody, reason);
        });
    }

    /** Return bounded quarantine evidence for an authenticated operator. */
    public List<Snapshot> list(int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Limit must be between 1 and 200");
        }
        return independent.execute(transaction -> List.copyOf(jdbc.query("""
                SELECT evidence_id, body_json, reason, quarantined_at
                FROM reliable_quarantine ORDER BY quarantined_at DESC LIMIT ?
                """, (row, index) -> readSnapshot(row), limit)));
    }

    /** Read one evidence item for an explicit replay workflow. */
    public Snapshot find(String evidenceId) {
        requireEvidence(evidenceId);
        return independent.execute(transaction -> jdbc.query("""
                SELECT evidence_id, body_json, reason, quarantined_at
                FROM reliable_quarantine WHERE evidence_id = ?
                """, (row, index) -> readSnapshot(row), evidenceId).stream().findFirst().orElse(null));
    }

    private static Snapshot readSnapshot(ResultSet row) throws SQLException {
        Timestamp quarantinedAt = row.getTimestamp("quarantined_at");
        return new Snapshot(row.getString("evidence_id"), row.getString("body_json"),
                row.getString("reason"), quarantinedAt == null ? null : quarantinedAt.toInstant());
    }

    private static void requireEvidence(String evidenceId) {
        if (evidenceId == null || evidenceId.isBlank()) {
            throw new IllegalArgumentException("Quarantine evidence ID is required");
        }
        if (evidenceId.length() > 128) {
            throw new IllegalArgumentException("Quarantine evidence ID is too long");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Quarantine reason is required");
        }
        if (reason.length() > 256) {
            throw new IllegalArgumentException("Quarantine reason is too long");
        }
    }

    public record Snapshot(String evidenceId, String bodyJson, String reason, Instant quarantinedAt) { }
}
