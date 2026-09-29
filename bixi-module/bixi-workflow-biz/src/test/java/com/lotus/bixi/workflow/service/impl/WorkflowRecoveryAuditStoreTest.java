package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRecoveryAuditStoreTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void exposesPersistentRecoveryRequestLookupAndInsert() {
        assertThat(Arrays.stream(WorkflowRecoveryAuditStore.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .contains("findRequest", "recordRequest");
    }

    @Test
    void requestReplayIdentityIsIndependentPerTenant() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow_recovery_audit_tenant;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS wf_recovery_audit");
        jdbc.execute("""
                CREATE TABLE wf_recovery_audit (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_scope VARCHAR(32) NOT NULL,
                    actor_id BIGINT NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    owner VARCHAR(64) NOT NULL,
                    event_id VARCHAR(36),
                    evidence_id VARCHAR(128),
                    changed BOOLEAN NOT NULL,
                    reason VARCHAR(256) NOT NULL,
                    request_id VARCHAR(36),
                    expected_status VARCHAR(32),
                    resource_type VARCHAR(32),
                    resource_id VARCHAR(128),
                    previous_status VARCHAR(32),
                    current_status VARCHAR(32),
                    outcome VARCHAR(32),
                    detail VARCHAR(256),
                    completed_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    UNIQUE (tenant_scope, owner, action, request_id)
                )
                """);
        var store = new WorkflowRecoveryAuditStore(dataSource, new DataSourceTransactionManager(dataSource));
        String requestId = "00000000-0000-0000-0000-000000000099";

        TenantContextHolder.set(41L);
        store.recordRequest(7L, "EVENT_RETRY", "workflow", "event-41", "tenant 41", "FAILED",
                result(requestId, "event-41", "tenant-41"));
        TenantContextHolder.set(42L);
        store.recordRequest(8L, "EVENT_RETRY", "workflow", "event-42", "tenant 42", "FAILED",
                result(requestId, "event-42", "tenant-42"));

        assertThat(store.findRequest("workflow", "EVENT_RETRY", requestId).result().resourceId())
                .isEqualTo("event-42");
        TenantContextHolder.set(41L);
        assertThat(store.findRequest("workflow", "EVENT_RETRY", requestId).result().resourceId())
                .isEqualTo("event-41");
    }

    @Test
    void unresolvedLegacyReplayIdentityBlocksMutationForEveryTenant() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:workflow_recovery_audit_legacy;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE wf_recovery_audit (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_scope VARCHAR(32) NOT NULL,
                    actor_id BIGINT NOT NULL,
                    action VARCHAR(32) NOT NULL,
                    owner VARCHAR(64) NOT NULL,
                    event_id VARCHAR(36),
                    evidence_id VARCHAR(128),
                    changed BOOLEAN NOT NULL,
                    reason VARCHAR(256) NOT NULL,
                    request_id VARCHAR(36),
                    expected_status VARCHAR(32),
                    resource_type VARCHAR(32),
                    resource_id VARCHAR(128),
                    previous_status VARCHAR(32),
                    current_status VARCHAR(32),
                    outcome VARCHAR(32),
                    detail VARCHAR(256),
                    completed_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    UNIQUE (tenant_scope, owner, action, request_id)
                )
                """);
        String requestId = "00000000-0000-0000-0000-000000000098";
        jdbc.update("""
                INSERT INTO wf_recovery_audit
                    (tenant_scope, actor_id, action, owner, changed, reason, request_id)
                VALUES ('legacy-unresolved', 7, 'EVENT_RETRY', 'workflow', 0, 'legacy', ?)
                """, requestId);
        var store = new WorkflowRecoveryAuditStore(dataSource, new DataSourceTransactionManager(dataSource));
        TenantContextHolder.set(42L);

        assertThatThrownBy(() -> store.findRequest("workflow", "EVENT_RETRY", requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("recovery_request_tenant_unresolved");
    }

    private static WorkflowRecoveryResultVO result(String requestId, String resourceId, String detail) {
        return new WorkflowRecoveryResultVO(requestId, "workflow", "OUTBOX", resourceId,
                "FAILED", "PENDING", true, "RETRIED", detail, Instant.parse("2026-09-29T00:00:00Z"));
    }
}
