package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.mq.reliable.RecoveryTenantScope;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Objects;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Persists UPMS recovery actions in the caller transaction when one is active. */
@Service
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
public class UpmsRecoveryAuditStore {
    private final JdbcTemplate jdbc;

    public UpmsRecoveryAuditStore(DataSource dataSource, DataSourceTransactionManager transactionManager) {
        Objects.requireNonNull(dataSource, "DataSource is required");
        if (transactionManager == null || transactionManager.getDataSource() != dataSource) {
            throw new IllegalArgumentException("A local JDBC transaction manager for the same DataSource is required");
        }
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void record(Long actorId, String action, String owner, String eventId, String evidenceId,
            boolean changed, String reason) {
        if (actorId == null || action == null || owner == null || reason == null) {
            throw new IllegalArgumentException("Recovery audit identity is required");
        }
        jdbc.update("""
                INSERT INTO wf_recovery_audit
                    (tenant_scope, actor_id, action, owner, event_id, evidence_id, changed, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                """, RecoveryTenantScope.currentScope(), actorId, action, owner, eventId, evidenceId,
                changed ? 1 : 0, reason);
    }

    public SavedRequest findRequest(String owner, String action, String requestId) {
        SavedRequest saved = jdbc.query("""
                SELECT reason, expected_status, resource_type, resource_id, previous_status,
                       current_status, changed, outcome, detail, completed_at
                  FROM wf_recovery_audit
                 WHERE tenant_scope = ? AND owner = ? AND action = ? AND request_id = ?
                """, (row, index) -> new SavedRequest(row.getString("reason"),
                row.getString("expected_status"), new WorkflowRecoveryResultVO(requestId, owner,
                row.getString("resource_type"), row.getString("resource_id"),
                row.getString("previous_status"), row.getString("current_status"),
                row.getBoolean("changed"), row.getString("outcome"), row.getString("detail"),
                instant(row.getObject("completed_at")))), RecoveryTenantScope.currentScope(), owner, action, requestId)
                .stream().findFirst().orElse(null);
        if (saved == null && unresolvedLegacyRequest(owner, action, requestId)) {
            throw new IllegalStateException("recovery_request_tenant_unresolved");
        }
        return saved;
    }

    public void recordRequest(Long actorId, String action, String owner, String eventId,
            String reason, String expectedStatus, WorkflowRecoveryResultVO result) {
        if (actorId == null || action == null || owner == null || reason == null
                || expectedStatus == null || result == null) {
            throw new IllegalArgumentException("Recovery request audit identity is required");
        }
        jdbc.update("""
                INSERT INTO wf_recovery_audit
                    (tenant_scope, actor_id, action, owner, event_id, changed, reason, request_id,
                     expected_status, resource_type, resource_id, previous_status,
                     current_status, outcome, detail, completed_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                """, RecoveryTenantScope.currentScope(), actorId, action, owner, eventId,
                result.changed() ? 1 : 0, reason,
                result.requestId(), expectedStatus, result.resourceType(), result.resourceId(),
                result.previousStatus(), result.currentStatus(), result.outcome(), result.detail(),
                java.sql.Timestamp.from(result.completedAt()));
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        return Instant.parse(value.toString());
    }

    private boolean unresolvedLegacyRequest(String owner, String action, String requestId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM wf_recovery_audit
                 WHERE tenant_scope = 'legacy-unresolved'
                   AND owner = ? AND action = ? AND request_id = ?
                """, Long.class, owner, action, requestId);
        return count != null && count > 0;
    }

    public record SavedRequest(String reason, String expectedStatus, WorkflowRecoveryResultVO result) { }
}
