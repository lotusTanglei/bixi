package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryActionVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRuntimeDiagnosticsVO;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.job.api.JobQuery;
import org.flowable.job.api.TimerJobQuery;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowRecoveryServiceTest {

    @Test
    void failedOutboxRetryUsesCompareAndSetAndWritesAnAuditRecord() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(access.currentUser()).thenReturn(actor);
        when(outbox.retry("workflow", "00000000-0000-0000-0000-000000000007")).thenReturn(true);

        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox, inbox, quarantine, audit, access,
                mock(InboxExecutor.class));
        WorkflowRecoveryActionVO result = service.retryOutbox(
                "00000000-0000-0000-0000-000000000007", "人工恢复");

        assertThat(result.changed()).isTrue();
        assertThat(result.direction()).isEqualTo("OUTBOX");
        verify(audit).record(7L, "OUTBOX_RETRY", "workflow",
                "00000000-0000-0000-0000-000000000007", null, true, "人工恢复");
    }

    @Test
    void retryRejectsBlankReasonAndDoesNotTouchTheStore() {
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class),
                mock(InboxExecutor.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.retryOutbox(
                "00000000-0000-0000-0000-000000000007", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replayQuarantineDecodesWireAndUsesTheOwnerInboxExecutor() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(access.currentUser()).thenReturn(actor);
        String eventId = "00000000-0000-0000-0000-000000000009";
        DurableMessage message = DurableMessage.create("upms", "workflow", eventId,
                "WORKFLOW_BUSINESS_TASK_RESULT", 1, "{\"ok\":true}");
        String body = new String(new DurableMessageWireCodec().encode(message), java.nio.charset.StandardCharsets.UTF_8);
        when(quarantine.find("evidence-9")).thenReturn(new JdbcQuarantineStore.Snapshot(
                "evidence-9", body, "PERMANENT", Instant.parse("2026-09-22T00:00:00Z")));
        when(executor.receive(message)).thenReturn(DurableMessageHandler.Result.PROCESSED);

        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox, inbox, quarantine, audit, access, executor);
        var result = service.replayQuarantine("evidence-9", "修复路由后重放");

        assertThat(result.evidenceId()).isEqualTo("evidence-9");
        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.result()).isEqualTo("PROCESSED");
        assertThat(result.accepted()).isTrue();
        verify(executor).receive(message);
        verify(audit).record(7L, "QUARANTINE_REPLAY", "workflow", eventId,
                "evidence-9", true, "修复路由后重放");
    }

    @Test
    void reconcileMarksRunningProcessWithoutDurableEventAsPendingDelivery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HashMap<String, Object> row = new HashMap<>();
        row.put("business_id", 41L);
        row.put("business_key", "demo_leave:41:1");
        row.put("process_instance_id", "process-41");
        row.put("round", 1);
        row.put("business_table", "demo_leave_request");
        row.put("business_status", "running");
        row.put("request_id", "00000000-0000-0000-0000-000000000011");
        row.put("command_status", "SUCCEEDED");
        row.put("operation_id", "00000000-0000-0000-0000-000000000042");
        row.put("business_task_status", "DELIVERED");
        row.put("compensation_id", "00000000-0000-0000-0000-000000000043");
        row.put("event_id", null);
        row.put("event_type", null);
        row.put("durable_status", null);
        when(jdbc.queryForList(anyString(), eq(20))).thenReturn(List.of(row));

        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class), jdbc);

        assertThat(service.reconcile(20)).singleElement().satisfies(item -> {
            assertThat(item.processInstanceId()).isEqualTo("process-41");
            assertThat(item.requestId()).isEqualTo("00000000-0000-0000-0000-000000000011");
            assertThat(item.commandStatus()).isEqualTo("SUCCEEDED");
            assertThat(item.operationId()).isEqualTo("00000000-0000-0000-0000-000000000042");
            assertThat(item.businessTaskStatus()).isEqualTo("DELIVERED");
            assertThat(item.compensationId()).isEqualTo("00000000-0000-0000-0000-000000000043");
            assertThat(item.classification()).isEqualTo("PENDING_DELIVERY");
        });
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(20));
        assertThat(sql.getValue())
                .contains("p.start_request_id AS request_id", "FROM wf_command c",
                        "$.payload.operationId", "WORKFLOW_BUSINESS_TASK_REQUESTED",
                        "$.payload.compensationId");
    }

    @Test
    void reconcileReportsAnUnresolvedQuarantinedEventByBusinessCorrelation() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        HashMap<String, Object> row = new HashMap<>();
        row.put("business_id", 41L);
        row.put("business_key", "demo_leave:41:1");
        row.put("process_instance_id", "process-41");
        row.put("round", 1);
        row.put("business_table", "demo_leave_request");
        row.put("business_status", "running");
        row.put("event_id", null);
        row.put("event_type", null);
        row.put("durable_status", null);
        when(jdbc.queryForList(anyString(), eq(20))).thenReturn(List.of(row));

        String eventId = "00000000-0000-0000-0000-000000000041";
        String payload = """
                {"eventId":"00000000-0000-0000-0000-000000000041",
                 "type":"WORKFLOW_BUSINESS_TASK_RESULT","schemaVersion":2,
                 "sourceOwner":"upms","targetOwner":"workflow","tenantScope":"default",
                 "processInstanceId":"process-41","processKey":"demo_leave_approval",
                 "businessTable":"demo_leave_request","businessId":41,
                 "businessKey":"demo_leave:41:1","round":1,
                 "commandId":"00000000-0000-0000-0000-000000000011",
                 "aggregateSequence":3,"occurredAt":"2026-09-22T00:00:00Z",
                 "correlationId":"00000000-0000-0000-0000-000000000011",
                 "causationId":null,"actor":{},
                 "payload":{"operationId":"00000000-0000-0000-0000-000000000042"}}
                """;
        DurableMessage message = DurableMessage.create("upms", "workflow", eventId,
                "WORKFLOW_BUSINESS_TASK_RESULT", 2, payload);
        String wire = new String(new DurableMessageWireCodec().encode(message),
                java.nio.charset.StandardCharsets.UTF_8);
        when(quarantine.list(20)).thenReturn(List.of(new JdbcQuarantineStore.Snapshot(
                eventId, wire, "PERMANENT", Instant.parse("2026-09-22T00:01:00Z"))));
        when(inbox.find("workflow", eventId)).thenReturn(null);

        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                inbox, quarantine, mock(WorkflowRecoveryAuditStore.class),
                mock(WorkflowAccessService.class), jdbc);

        assertThat(service.reconcile(20)).singleElement().satisfies(item -> {
            assertThat(item.eventId()).isEqualTo(eventId);
            assertThat(item.eventType()).isEqualTo("WORKFLOW_BUSINESS_TASK_RESULT");
            assertThat(item.requestId()).isEqualTo("00000000-0000-0000-0000-000000000011");
            assertThat(item.operationId()).isEqualTo("00000000-0000-0000-0000-000000000042");
            assertThat(item.quarantineEvidenceId()).isEqualTo(eventId);
            assertThat(item.classification()).isEqualTo("QUARANTINED");
            assertThat(item.detail()).contains("PERMANENT");
        });
    }

    @Test
    void diagnosticsExposeRuntimeConfigurationAndRedactedDurableFailureState() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ProcessEngine processEngine = mock(ProcessEngine.class);
        ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
        org.flowable.job.service.impl.asyncexecutor.AsyncExecutor asyncExecutor =
                mock(org.flowable.job.service.impl.asyncexecutor.AsyncExecutor.class);
        ManagementService management = mock(ManagementService.class);
        JobQuery executableJobs = mock(JobQuery.class);
        TimerJobQuery timerJobs = mock(TimerJobQuery.class);
        DeadLetterJobQuery failedJobs = mock(DeadLetterJobQuery.class);

        when(processEngine.getProcessEngineConfiguration()).thenReturn(configuration);
        when(processEngine.getManagementService()).thenReturn(management);
        when(configuration.isAsyncExecutorActivate()).thenReturn(true);
        when(configuration.getAsyncExecutor()).thenReturn(asyncExecutor);
        when(configuration.isAsyncExecutorUnlockOwnedJobs()).thenReturn(true);
        when(asyncExecutor.getLockOwner()).thenReturn("workflow-test-a");
        when(asyncExecutor.isAutoActivate()).thenReturn(true);
        when(asyncExecutor.isActive()).thenReturn(true);
        when(asyncExecutor.getAsyncJobLockTimeInMillis()).thenReturn(7_000);
        when(asyncExecutor.getTimerLockTimeInMillis()).thenReturn(9_000);
        when(asyncExecutor.getResetExpiredJobsInterval()).thenReturn(11_000);
        when(asyncExecutor.getDefaultAsyncJobAcquireWaitTimeInMillis()).thenReturn(13_000);
        when(asyncExecutor.getDefaultTimerJobAcquireWaitTimeInMillis()).thenReturn(15_000);
        when(asyncExecutor.getMaxAsyncJobsDuePerAcquisition()).thenReturn(3);
        when(asyncExecutor.getMaxTimerJobsPerAcquisition()).thenReturn(4);
        when(asyncExecutor.getResetExpiredJobsPageSize()).thenReturn(25);
        when(configuration.isAsyncExecutorResetExpiredJobsEnabled()).thenReturn(true);
        when(management.createJobQuery()).thenReturn(executableJobs);
        when(management.createTimerJobQuery()).thenReturn(timerJobs);
        when(management.createDeadLetterJobQuery()).thenReturn(failedJobs);
        when(executableJobs.count()).thenReturn(6L);
        when(timerJobs.count()).thenReturn(2L);
        when(failedJobs.count()).thenReturn(1L);

        Map<String, Object> outboxStats = new HashMap<>();
        outboxStats.put("pending_count", 3L);
        outboxStats.put("failed_count", 1L);
        LocalDateTime oldestOutbox = LocalDateTime.of(2026, 9, 22, 0, 0);
        LocalDateTime oldestInbox = LocalDateTime.of(2026, 9, 22, 1, 0);
        LocalDateTime recentFailure = LocalDateTime.of(2026, 9, 22, 2, 0);
        outboxStats.put("oldest_waiting_at", oldestOutbox);
        Map<String, Object> inboxStats = new HashMap<>();
        inboxStats.put("pending_count", 2L);
        inboxStats.put("failed_count", 4L);
        inboxStats.put("oldest_waiting_at", oldestInbox);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(outboxStats, inboxStats);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "source", "INBOX",
                "event_id", "00000000-0000-0000-0000-000000000099",
                "last_error", "java.lang.IllegalStateException",
                "occurred_at", recentFailure
        )));

        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class), jdbc,
                processEngine);

        WorkflowRuntimeDiagnosticsVO result = service.diagnostics();

        assertThat(result.lockOwner()).isEqualTo("workflow-test-a");
        assertThat(result.asyncExecutorActive()).isTrue();
        assertThat(result.asyncJobLockTimeMillis()).isEqualTo(7_000);
        assertThat(result.outboxPendingCount()).isEqualTo(3L);
        assertThat(result.inboxFailedCount()).isEqualTo(4L);
        assertThat(result.oldestOutboxWaitingAt()).isEqualTo(oldestOutbox.toInstant(ZoneOffset.UTC));
        assertThat(result.oldestInboxWaitingAt()).isEqualTo(oldestInbox.toInstant(ZoneOffset.UTC));
        assertThat(result.flowableExecutableJobCount()).isEqualTo(6L);
        assertThat(result.flowableTimerJobCount()).isEqualTo(2L);
        assertThat(result.flowableFailedJobCount()).isEqualTo(1L);
        assertThat(result.recentFailureSource()).isEqualTo("INBOX");
        assertThat(result.recentFailureEventId()).isEqualTo("00000000-0000-0000-0000-000000000099");
        assertThat(result.recentFailureSummary()).isEqualTo("java.lang.IllegalStateException");
        assertThat(result.recentFailureAt()).isEqualTo(recentFailure.toInstant(ZoneOffset.UTC));
        assertThat(result.toString()).doesNotContain("payload");
        verify(jdbc).queryForMap(contains("status IN ('RECEIVED', 'IN_FLIGHT')"), eq("workflow"));
    }
}
