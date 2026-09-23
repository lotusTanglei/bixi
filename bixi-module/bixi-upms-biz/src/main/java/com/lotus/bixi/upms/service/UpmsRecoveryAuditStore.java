package com.lotus.bixi.upms.service;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/** Persists UPMS recovery actions independently from delivery transactions. */
@Service
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
public class UpmsRecoveryAuditStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public UpmsRecoveryAuditStore(DataSource dataSource, DataSourceTransactionManager transactionManager) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Long actorId, String action, String owner, String eventId, String evidenceId,
            boolean changed, String reason) {
        if (actorId == null || action == null || owner == null || reason == null) {
            throw new IllegalArgumentException("Recovery audit identity is required");
        }
        transaction.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO wf_recovery_audit
                    (actor_id, action, owner, event_id, evidence_id, changed, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
                """, actorId, action, owner, eventId, evidenceId, changed ? 1 : 0, reason));
    }
}
