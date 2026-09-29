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
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkflowRecoveryServiceTest {

    @Test
    void exposesUnifiedEventRetryWithStableRequestIdentity() {
        assertThat(java.util.Arrays.stream(WorkflowRecoveryService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("retryEvent"))
                .map(java.lang.reflect.Method::getParameterTypes))
                .anySatisfy(parameters -> assertThat(parameters)
                        .containsExactly(String.class, WorkflowRecoveryRequestDTO.class));
    }

    @Test
    void rejectsMissingRecoveryRequestFieldsBeforeTouchingOwnerStores() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox,
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class),
                mock(InboxExecutor.class));

        assertThatThrownBy(() -> service.retryEvent(
                "00000000-0000-0000-0000-000000000007",
                new WorkflowRecoveryRequestDTO("", "reason", "FAILED")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsCrossOwnerRecoveryQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class),
                jdbc);
        var query = new com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO();
        query.setOwner("upms");

        assertThatThrownBy(() -> service.pageEvents(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void rejectsRecoveryPageSizeOutsideTheBoundedRange() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class), jdbc);
        var query = new com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO();
        query.setSize(201);

        assertThatThrownBy(() -> service.pageEvents(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 200");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsRecoveryPageWhenOffsetWouldOverflowLongBeforeQuerying() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class), jdbc);
        var query = new com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO();
        query.setCurrent(Long.MAX_VALUE);
        query.setSize(2);

        assertThatThrownBy(() -> service.pageEvents(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
        verifyNoInteractions(jdbc);
    }

    @Test
    void eventInventoryKeepsBusinessIdsAsExactDecimalText() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class), jdbc);
        var query = new com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO();
        query.setBusinessId(2_102_245_941_578_928_130L);

        service.pageEvents(query);

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        assertThat(sql.getValue())
                .doesNotContain("CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessId')) AS UNSIGNED)")
                .contains("JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessId')) AS business_id");
    }

    @Test
    void reconcileBusinessTaskPersistsReplayableRequestResult() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(access.currentUser()).thenReturn(actor);
        String operationId = "00000000-0000-0000-0000-000000000042";
        HashMap<String, Object> row = new HashMap<>();
        row.put("operation_id", operationId);
        row.put("process_instance_id", "process-42");
        row.put("business_table", "demo_leave_request");
        row.put("business_id", 42L);
        row.put("round", 1);
        row.put("status", "SUCCEEDED");
        row.put("result_event_id", "00000000-0000-0000-0000-000000000043");
        row.put("compensation_id", null);
        row.put("deadline", null);
        row.put("last_error", null);
        row.put("created_at", java.sql.Timestamp.valueOf("2026-09-26 10:00:00"));
        row.put("updated_at", java.sql.Timestamp.valueOf("2026-09-26 10:01:00"));
        when(jdbc.queryForList(anyString(), eq(operationId), eq("1"))).thenReturn(List.of(row));

        WorkflowRecoveryService service = new WorkflowRecoveryService(
                mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                audit, access, jdbc);
        WorkflowRecoveryRequestDTO request = new WorkflowRecoveryRequestDTO(
                "00000000-0000-0000-0000-000000000044", "核对登记状态", "SUCCEEDED");

        var result = service.reconcileBusinessTask(operationId, request);

        assertThat(result.outcome()).isEqualTo("MATCHED");
        verify(audit).recordRequest(eq(7L), eq("BUSINESS_TASK_RECONCILE"), eq("workflow"),
                eq(operationId), eq("核对登记状态"), eq("SUCCEEDED"), eq(result));
    }

    @Test
    void duplicateBusinessTaskReconcileReturnsTheCommittedResultWithoutRequeryingTask() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        String operationId = "00000000-0000-0000-0000-000000000042";
        String requestId = "00000000-0000-0000-0000-000000000044";
        var saved = new com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO(requestId, "workflow",
                "BUSINESS_TASK", operationId, "SUCCEEDED", "SUCCEEDED", false,
                "MATCHED", "工作流任务已处于稳定状态", Instant.parse("2026-09-26T02:01:00Z"));
        when(audit.findRequest("workflow", "BUSINESS_TASK_RECONCILE", requestId))
                .thenReturn(new WorkflowRecoveryAuditStore.SavedRequest("核对登记状态", "SUCCEEDED", saved));

        WorkflowRecoveryService service = new WorkflowRecoveryService(
                mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                audit, access, jdbc);

        var result = service.reconcileBusinessTask(operationId,
                new WorkflowRecoveryRequestDTO(requestId, "核对登记状态", "SUCCEEDED"));

        assertThat(result).isSameAs(saved);
        verifyNoInteractions(jdbc, access);
        verify(audit, never()).recordRequest(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void rejectsMismatchedWinnerAfterBusinessTaskReconcileRace() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(access.currentUser()).thenReturn(actor);
        String operationId = "00000000-0000-0000-0000-000000000042";
        String requestId = "00000000-0000-0000-0000-000000000044";
        HashMap<String, Object> row = new HashMap<>();
        row.put("operation_id", operationId);
        row.put("process_instance_id", "process-42");
        row.put("business_table", "demo_leave_request");
        row.put("business_id", 42L);
        row.put("round", 1);
        row.put("status", "SUCCEEDED");
        row.put("result_event_id", null);
        row.put("compensation_id", null);
        row.put("deadline", null);
        row.put("last_error", null);
        row.put("created_at", java.sql.Timestamp.valueOf("2026-09-26 10:00:00"));
        row.put("updated_at", java.sql.Timestamp.valueOf("2026-09-26 10:01:00"));
        when(jdbc.queryForList(anyString(), eq(operationId), eq("1"))).thenReturn(List.of(row));
        doThrow(new org.springframework.dao.DuplicateKeyException("race")).when(audit).recordRequest(
                eq(7L), eq("BUSINESS_TASK_RECONCILE"), eq("workflow"), eq(operationId),
                eq("核对登记状态"), eq("SUCCEEDED"), any());
        var winner = new WorkflowRecoveryResultVO(requestId, "workflow", "BUSINESS_TASK",
                "00000000-0000-0000-0000-000000000099", "SUCCEEDED", "SUCCEEDED", false,
                "MATCHED", "winner", Instant.parse("2026-09-26T02:01:00Z"));
        when(audit.findRequest("workflow", "BUSINESS_TASK_RECONCILE", requestId))
                .thenReturn(null, new WorkflowRecoveryAuditStore.SavedRequest(
                        "核对登记状态", "SUCCEEDED", winner));

        WorkflowRecoveryService service = new WorkflowRecoveryService(
                mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                audit, access, jdbc);

        assertThatThrownBy(() -> service.reconcileBusinessTask(operationId,
                new WorkflowRecoveryRequestDTO(requestId, "核对登记状态", "SUCCEEDED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestId");
    }

    @Test
    void activeInFlightEventCannotBeClaimedByRecovery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        String eventId = "00000000-0000-0000-0000-000000000045";
        HashMap<String, Object> row = new HashMap<>();
        row.put("direction", "INBOX");
        row.put("event_owner", "workflow");
        row.put("peer_owner", "upms");
        row.put("event_id", eventId);
        row.put("event_type", "WORKFLOW_BUSINESS_TASK_RESULT");
        row.put("schema_version", 2);
        row.put("payload_hash", "hash");
        row.put("event_status", "IN_FLIGHT");
        row.put("attempts", 1);
        row.put("lease_until", Instant.now().plusSeconds(60));
        row.put("last_error", null);
        row.put("created_at", Instant.now());
        row.put("completed_at", null);
        when(jdbc.queryForList(anyString(), eq("workflow"), eq(eventId), eq("1"))).thenReturn(List.of(row));

        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox, inbox,
                mock(JdbcQuarantineStore.class), audit, mock(WorkflowAccessService.class), jdbc);

        assertThatThrownBy(() -> service.retryEvent(eventId,
                new WorkflowRecoveryRequestDTO("00000000-0000-0000-0000-000000000046",
                        "避免抢占", "IN_FLIGHT")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("活跃租约");
        verifyNoInteractions(outbox, inbox);
    }

    @Test
    void listQuarantineReturnsMetadataWithoutExposingTheWireBody() {
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        Instant quarantinedAt = Instant.parse("2026-09-22T00:00:00Z");
        DurableMessage message = DurableMessage.create("upms", "workflow",
                "00000000-0000-0000-0000-000000000008", "WORKFLOW_TEST", 1,
                "{\"tenantScope\":\"default\"}");
        String wire = new String(new DurableMessageWireCodec().encode(message),
                java.nio.charset.StandardCharsets.UTF_8);
        when(quarantine.listPage(200, 0L)).thenReturn(List.of(new JdbcQuarantineStore.Snapshot(
                "evidence-secret", wire, "PERMANENT", quarantinedAt)));
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), quarantine, mock(WorkflowRecoveryAuditStore.class),
                mock(WorkflowAccessService.class), mock(InboxExecutor.class));

        assertThat(service.listQuarantine(20)).singleElement().satisfies(row -> {
            assertThat(row.evidenceId()).isEqualTo("evidence-secret");
            assertThat(row.bodyJson()).isNull();
            assertThat(row.reason()).isEqualTo("PERMANENT");
            assertThat(row.quarantinedAt()).isEqualTo(quarantinedAt);
        });
    }

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
        String eventId = "00000000-0000-0000-0000-000000000007";
        DurableMessage message = DurableMessage.create("workflow", "upms", eventId,
                "WORKFLOW_TEST", 1, "{\"tenantScope\":\"default\"}");
        when(outbox.find("workflow", eventId)).thenReturn(new JdbcOutboxStore.AdminSnapshot(
                message, "dedup", null, null, "FAILED", 1, Instant.now(), null, null, Instant.now(), null));
        doAnswer(invocation -> {
            java.util.function.Consumer<Boolean> auditCallback = invocation.getArgument(2);
            auditCallback.accept(true);
            return true;
        }).when(outbox).retry(eq("workflow"), eq("00000000-0000-0000-0000-000000000007"), any());

        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox, inbox, quarantine, audit, access,
                mock(InboxExecutor.class));
        WorkflowRecoveryActionVO result = service.retryOutbox(
                "00000000-0000-0000-0000-000000000007", "人工恢复");

        assertThat(result.changed()).isTrue();
        assertThat(result.direction()).isEqualTo("OUTBOX");
        verify(audit).record(7L, "OUTBOX_RETRY", "workflow",
                "00000000-0000-0000-0000-000000000007", null, true, "人工恢复");
        verify(outbox).retry(eq("workflow"), eq("00000000-0000-0000-0000-000000000007"), any());
        verify(outbox, never()).retry("workflow", "00000000-0000-0000-0000-000000000007");
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
                "WORKFLOW_BUSINESS_TASK_RESULT", 1, "{\"ok\":true,\"tenantScope\":\"default\"}");
        String body = new String(new DurableMessageWireCodec().encode(message), java.nio.charset.StandardCharsets.UTF_8);
        when(quarantine.find("evidence-9")).thenReturn(new JdbcQuarantineStore.Snapshot(
                "evidence-9", body, "PERMANENT", Instant.parse("2026-09-22T00:00:00Z")));
        when(executor.replay(eq(message), any())).thenAnswer(invocation -> {
            java.util.function.Consumer<DurableMessageHandler.Result> auditCallback = invocation.getArgument(1);
            auditCallback.accept(DurableMessageHandler.Result.PROCESSED);
            return DurableMessageHandler.Result.PROCESSED;
        });

        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox, inbox, quarantine, audit, access, executor);
        var result = service.replayQuarantine("evidence-9", "修复路由后重放");

        assertThat(result.evidenceId()).isEqualTo("evidence-9");
        assertThat(result.eventId()).isEqualTo(eventId);
        assertThat(result.result()).isEqualTo("PROCESSED");
        assertThat(result.accepted()).isTrue();
        verify(executor).replay(eq(message), any());
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
        row.put("business_task_status", "COMPENSATING");
        row.put("compensation_id", "00000000-0000-0000-0000-000000000043");
        row.put("event_id", null);
        row.put("event_type", null);
        row.put("durable_status", null);
        when(jdbc.queryForList(anyString(), eq("1"), eq(20))).thenReturn(List.of(row));

        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(WorkflowRecoveryAuditStore.class), mock(WorkflowAccessService.class), jdbc);

        assertThat(service.reconcile(20)).singleElement().satisfies(item -> {
            assertThat(item.processInstanceId()).isEqualTo("process-41");
            assertThat(item.requestId()).isEqualTo("00000000-0000-0000-0000-000000000011");
            assertThat(item.commandStatus()).isEqualTo("SUCCEEDED");
            assertThat(item.operationId()).isEqualTo("00000000-0000-0000-0000-000000000042");
            assertThat(item.businessTaskStatus()).isEqualTo("COMPENSATING");
            assertThat(item.compensationId()).isEqualTo("00000000-0000-0000-0000-000000000043");
            assertThat(item.classification()).isEqualTo("PENDING_DELIVERY");
        });
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq("1"), eq(20));
        assertThat(sql.getValue())
                .contains("p.start_request_id AS request_id", "FROM wf_command c",
                        "FROM wf_business_task t", "t.status", "t.compensation_id");
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
        when(jdbc.queryForList(anyString(), eq("1"), eq(20))).thenReturn(List.of(row));

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
        when(quarantine.listPage(200, 0L)).thenReturn(List.of(new JdbcQuarantineStore.Snapshot(
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
        verify(jdbc).queryForMap(contains("status IN ('RECEIVED', 'IN_FLIGHT')"), eq("workflow"), eq("1"));
    }
}
