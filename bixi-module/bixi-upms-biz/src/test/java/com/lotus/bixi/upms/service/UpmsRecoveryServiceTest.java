package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryActionVO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.time.Instant;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UpmsRecoveryServiceTest {

    @BeforeEach
    void setTenant() {
        TenantContextHolder.set(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsMissingRecoveryRequestFieldsBeforeTouchingOwnerStores() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        UpmsRecoveryService service = new UpmsRecoveryService(outbox, mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(UpmsRecoveryAuditStore.class),
                mock(UpmsRecoveryAccessService.class), mock(InboxExecutor.class));

        assertThatThrownBy(() -> service.retryLeaveEvent(
                "00000000-0000-0000-0000-000000000007",
                new WorkflowRecoveryRequestDTO("", "reason", "FAILED")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void rejectsCrossOwnerRecoveryQuery() {
        WorkflowRecoveryQueryDTO query = new WorkflowRecoveryQueryDTO();
        query.setOwner("workflow");
        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(UpmsRecoveryAuditStore.class), mock(UpmsRecoveryAccessService.class),
                mock(JdbcTemplate.class));

        assertThatThrownBy(() -> service.pageLeaveEvents(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");
    }

    @Test
    void rejectsRecoveryPageSizeOutsideTheBoundedRange() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(UpmsRecoveryAuditStore.class), mock(UpmsRecoveryAccessService.class), jdbc);
        var query = new WorkflowRecoveryQueryDTO();
        query.setSize(201);

        assertThatThrownBy(() -> service.pageLeaveEvents(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 200");
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsRecoveryPageWhenOffsetWouldOverflowLongBeforeQuerying() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(actor.getTenantId()).thenReturn(1L);
        when(access.currentUser()).thenReturn(actor);
        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(UpmsRecoveryAuditStore.class), access, jdbc);
        var query = new WorkflowRecoveryQueryDTO();
        query.setCurrent(Long.MAX_VALUE);
        query.setSize(2);

        assertThatThrownBy(() -> service.pageLeaveEvents(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
        verifyNoInteractions(jdbc, access);
    }

    @Test
    void rejectsMismatchedWinnerAfterLeaveCommandRecoveryRace() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(actor.getTenantId()).thenReturn(1L);
        when(access.currentUser()).thenReturn(actor);
        String commandId = "00000000-0000-0000-0000-000000000042";
        String requestId = "00000000-0000-0000-0000-000000000044";
        HashMap<String, Object> command = new HashMap<>();
        command.put("command_id", commandId);
        command.put("client_request_id", requestId);
        command.put("operation", "START");
        command.put("leave_id", 41L);
        command.put("status", "ACCEPTED");
        command.put("process_instance_id", null);
        command.put("business_key", "demo_leave:41:1");
        command.put("actor_id", 7L);
        command.put("error", null);
        command.put("created_at", java.sql.Timestamp.valueOf("2026-09-26 10:00:00"));
        command.put("completed_at", null);
        when(jdbc.queryForList(anyString(), eq(commandId), eq(7L), eq("1"))).thenReturn(List.of(command));
        doThrow(new org.springframework.dao.DuplicateKeyException("race")).when(audit).recordRequest(
                eq(7L), eq("LEAVE_COMMAND_RETRY"), eq("upms"), eq(commandId),
                eq("核对命令"), eq("ACCEPTED"), any());
        var winner = new WorkflowRecoveryResultVO(requestId, "upms", "COMMAND",
                "00000000-0000-0000-0000-000000000099", "ACCEPTED", "ACCEPTED", false,
                "MATCHED", "winner", Instant.parse("2026-09-26T02:01:00Z"));
        when(audit.findRequest("upms", "LEAVE_COMMAND_RETRY", requestId))
                .thenReturn(null, new UpmsRecoveryAuditStore.SavedRequest(
                        "核对命令", "ACCEPTED", winner));
        when(jdbc.queryForList(argThat(sql -> sql.contains("FROM reliable_outbox") && sql.contains("tenantScope")),
                eq(commandId), eq("1")))
                .thenReturn(List.of());

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class), audit, access, jdbc);

        assertThatThrownBy(() -> service.retryLeaveCommand(commandId,
                new WorkflowRecoveryRequestDTO(requestId, "核对命令", "ACCEPTED")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requestId");
        ArgumentCaptor<String> durableSql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(durableSql.capture(), eq(commandId), eq("1"));
        assertThat(durableSql.getValue())
                .contains("JSON_TYPE(JSON_EXTRACT(payload_json, '$.tenantScope')) = 'STRING'");
    }

    @Test
    void replayedLeaveRecoveryStillRequiresCurrentApplicantVisibility() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(8L);
        when(actor.getTenantId()).thenReturn(1L);
        when(access.currentUser()).thenReturn(actor);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        String eventId = "00000000-0000-0000-0000-000000000041";
        String commandId = "00000000-0000-0000-0000-000000000042";
        String operationId = "00000000-0000-0000-0000-000000000043";
        String eventRequestId = "00000000-0000-0000-0000-000000000051";
        String commandRequestId = "00000000-0000-0000-0000-000000000052";
        String bookingRequestId = "00000000-0000-0000-0000-000000000053";
        when(audit.findRequest("upms", "LEAVE_EVENT_RETRY", eventRequestId)).thenReturn(
                savedRequest(eventRequestId, "OUTBOX", eventId));
        when(audit.findRequest("upms", "LEAVE_COMMAND_RETRY", commandRequestId)).thenReturn(
                savedRequest(commandRequestId, "COMMAND", commandId));
        when(audit.findRequest("upms", "LEAVE_BOOKING_RECONCILE", bookingRequestId)).thenReturn(
                savedRequest(bookingRequestId, "BUSINESS_TASK", operationId));

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class), audit, access, jdbc);

        assertThatThrownBy(() -> service.retryLeaveEvent(eventId,
                new WorkflowRecoveryRequestDTO(eventRequestId, "operator reason", "FAILED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不可见");
        assertThatThrownBy(() -> service.retryLeaveCommand(commandId,
                new WorkflowRecoveryRequestDTO(commandRequestId, "operator reason", "FAILED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不可见");
        assertThatThrownBy(() -> service.reconcileLeaveBooking(operationId,
                new WorkflowRecoveryRequestDTO(bookingRequestId, "operator reason", "FAILED")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不可见");
        verify(jdbc, times(3)).queryForList(anyString(), any(Object[].class));
        verifyNoInteractions(audit);
    }

    @Test
    void listQuarantineReturnsMetadataWithoutExposingTheWireBody() {
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        Instant quarantinedAt = Instant.parse("2026-09-22T00:00:00Z");
        DurableMessage message = DurableMessage.create("workflow", "upms",
                "00000000-0000-0000-0000-000000000009", "WORKFLOW_TEST", 1,
                "{\"tenantScope\":\"1\"}");
        String wire = new String(new DurableMessageWireCodec().encode(message),
                java.nio.charset.StandardCharsets.UTF_8);
        when(quarantine.listPage(200, 0L)).thenReturn(List.of(new JdbcQuarantineStore.Snapshot(
                "evidence-secret", wire, "PERMANENT", quarantinedAt)));
        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), quarantine, mock(UpmsRecoveryAuditStore.class),
                mock(UpmsRecoveryAccessService.class), mock(InboxExecutor.class));

        assertThat(service.listQuarantine(20)).singleElement().satisfies(row -> {
            assertThat(row.evidenceId()).isEqualTo("evidence-secret");
            assertThat(row.bodyJson()).isNull();
            assertThat(row.reason()).isEqualTo("PERMANENT");
            assertThat(row.quarantinedAt()).isEqualTo(quarantinedAt);
        });
    }

    @Test
    void failedInboxRetryUsesCompareAndSetAndWritesAnAuditRecord() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(access.currentUser()).thenReturn(actor);
        String eventId = "00000000-0000-0000-0000-000000000007";
        DurableMessage message = DurableMessage.create("workflow", "upms", eventId,
                "WORKFLOW_TEST", 1, "{\"tenantScope\":\"1\"}");
        when(inbox.find("upms", eventId)).thenReturn(
                new JdbcInboxStore.Snapshot(message, JdbcInboxStore.State.FAILED, 1));
        doAnswer(invocation -> {
            java.util.function.Consumer<Boolean> auditCallback = invocation.getArgument(2);
            auditCallback.accept(true);
            return true;
        }).when(inbox).retry(eq("upms"), eq(eventId), any());

        UpmsRecoveryService service = new UpmsRecoveryService(outbox, inbox, quarantine, audit, access,
                mock(InboxExecutor.class));
        WorkflowRecoveryActionVO result = service.retryInbox(eventId, "人工恢复");

        assertThat(result.changed()).isTrue();
        assertThat(result.direction()).isEqualTo("INBOX");
        verify(audit).record(7L, "INBOX_RETRY", "upms", eventId, null, true, "人工恢复");
        verify(inbox).retry(eq("upms"), eq(eventId), any());
        verify(inbox, never()).retry("upms", eventId);
    }

    @Test
    void failedOutboxRetryRejectsInvalidIdentityBeforeCallingStore() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        UpmsRecoveryService service = new UpmsRecoveryService(outbox, mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(UpmsRecoveryAuditStore.class),
                mock(UpmsRecoveryAccessService.class), mock(InboxExecutor.class));

        assertThatThrownBy(() -> service.retryOutbox("not-a-uuid", "人工恢复"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void listOutboxMapsMetadataWithoutExposingPayload() {
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        DurableMessage message = DurableMessage.create("upms", "workflow",
                "00000000-0000-0000-0000-000000000008", "WORKFLOW_COMPLETED", 1,
                "{\"tenantScope\":\"1\",\"secret\":true}");
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        when(outbox.listPage("upms", "FAILED", 200, 0L)).thenReturn(List.of(new JdbcOutboxStore.AdminSnapshot(
                message, "dedup", "aggregate", 3L, "FAILED", 2, now, now, "java.lang.IllegalStateException", now, null)));

        UpmsRecoveryService service = new UpmsRecoveryService(outbox, mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(UpmsRecoveryAuditStore.class),
                mock(UpmsRecoveryAccessService.class), mock(InboxExecutor.class));
        var rows = service.listOutbox(" failed ", 20);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.sourceOwner()).isEqualTo("upms");
            assertThat(row.targetOwner()).isEqualTo("workflow");
            assertThat(row.payloadHash()).isEqualTo(message.payloadHash());
            assertThat(row.status()).isEqualTo("FAILED");
        });
        verify(outbox).listPage("upms", "FAILED", 200, 0L);
    }

    @Test
    void replayQuarantineRejectsInvalidWireBeforeActorAuditOrExecutorAccess() {
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        when(quarantine.find("evidence-invalid")).thenReturn(new JdbcQuarantineStore.Snapshot(
                "evidence-invalid", "not-json", "WIRE_FORMAT_INVALID", Instant.now()));

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), quarantine, audit, access, executor);

        assertThatThrownBy(() -> service.replayQuarantine("evidence-invalid", "确认坏消息不可重放"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(audit, access, executor);
    }

    @Test
    void reconcileMarksSubmittingLeaveWithoutDurableCommandAsPendingDelivery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        HashMap<String, Object> row = new HashMap<>();
        row.put("business_id", 41L);
        row.put("business_key", "demo_leave:41:1");
        row.put("process_instance_id", null);
        row.put("round", 1);
        row.put("business_status", "SUBMITTING");
        row.put("event_id", null);
        row.put("event_type", null);
        row.put("durable_status", null);
        when(jdbc.queryForList(anyString(), eq(1L), eq(20))).thenReturn(List.of(row));

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(UpmsRecoveryAuditStore.class), mock(UpmsRecoveryAccessService.class), jdbc);

        assertThat(service.reconcile(20)).singleElement().satisfies(item -> {
            assertThat(item.businessId()).isEqualTo(41L);
            assertThat(item.classification()).isEqualTo("PENDING_DELIVERY");
            assertThat(item.detail()).contains("尚未找到");
        });
    }

    @Test
    void reconcileAssociatesNineteenDigitLeaveWithDurableEventByExactBusinessKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        long businessId = 2_102_245_941_578_928_130L;
        HashMap<String, Object> row = new HashMap<>();
        row.put("business_id", businessId);
        row.put("business_key", "demo_leave:" + businessId + ":1");
        row.put("process_instance_id", "process-19-digit");
        row.put("round", 1);
        row.put("business_status", "IN_REVIEW");
        row.put("event_id", "00000000-0000-0000-0000-000000000019");
        row.put("event_type", "WORKFLOW_START_REQUESTED");
        row.put("durable_status", "DELIVERED");
        row.put("request_id", "00000000-0000-0000-0000-000000000011");
        row.put("command_status", "DELIVERED");
        row.put("operation_id", "00000000-0000-0000-0000-000000000043");
        row.put("business_task_status", "BOOKED");
        row.put("compensation_id", "00000000-0000-0000-0000-000000000044");
        when(jdbc.queryForList(anyString(), eq(1L), eq(20))).thenReturn(List.of(row));

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), mock(JdbcQuarantineStore.class),
                mock(UpmsRecoveryAuditStore.class), mock(UpmsRecoveryAccessService.class), jdbc);

        assertThat(service.reconcile(20)).singleElement().satisfies(item -> {
            assertThat(item.businessId()).isEqualTo(businessId);
            assertThat(item.requestId()).isEqualTo("00000000-0000-0000-0000-000000000011");
            assertThat(item.commandStatus()).isEqualTo("DELIVERED");
            assertThat(item.operationId()).isEqualTo("00000000-0000-0000-0000-000000000043");
            assertThat(item.businessTaskStatus()).isEqualTo("BOOKED");
            assertThat(item.compensationId()).isEqualTo("00000000-0000-0000-0000-000000000044");
            assertThat(item.classification()).isEqualTo("MATCHED");
        });

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(1L), eq(20));
        assertThat(sql.getValue()).contains("JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key",
                        "l.start_command_id AS request_id", "LEFT JOIN demo_leave_booking b")
                .doesNotContain("CAST(JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessId')) AS UNSIGNED)");
    }

    @Test
    void reconcileReportsOnlyUnresolvedQuarantineForTheOwner() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        HashMap<String, Object> row = new HashMap<>();
        row.put("business_id", 41L);
        row.put("business_key", "demo_leave:41:1");
        row.put("process_instance_id", "process-41");
        row.put("round", 1);
        row.put("business_status", "IN_REVIEW");
        row.put("event_id", "00000000-0000-0000-0000-000000000010");
        row.put("event_type", "WORKFLOW_START_REQUESTED");
        row.put("durable_status", "DELIVERED");
        when(jdbc.queryForList(anyString(), eq(1L), eq(20))).thenReturn(List.of(row));

        String unresolvedId = "00000000-0000-0000-0000-000000000041";
        DurableMessage unresolved = durable("workflow", "upms", unresolvedId,
                "WORKFLOW_BUSINESS_TASK_REQUESTED", "demo_leave:41:1", "process-41");
        String resolvedId = "00000000-0000-0000-0000-000000000042";
        DurableMessage resolved = durable("workflow", "upms", resolvedId,
                "WORKFLOW_BUSINESS_TASK_REQUESTED", "demo_leave:42:1", "process-42");
        when(quarantine.listPage(200, 0L)).thenReturn(List.of(
                quarantined(unresolved, "PERMANENT"), quarantined(resolved, "CONFLICT")));
        when(inbox.find("upms", unresolvedId)).thenReturn(null);
        when(inbox.find("upms", resolvedId)).thenReturn(new JdbcInboxStore.Snapshot(
                resolved, JdbcInboxStore.State.PROCESSED, 1));

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class), inbox,
                quarantine, mock(UpmsRecoveryAuditStore.class), mock(UpmsRecoveryAccessService.class), jdbc);

        assertThat(service.reconcile(20)).singleElement().satisfies(item -> {
            assertThat(item.eventId()).isEqualTo(unresolvedId);
            assertThat(item.operationId()).isEqualTo("00000000-0000-0000-0000-000000000043");
            assertThat(item.quarantineEvidenceId()).isEqualTo(unresolvedId);
            assertThat(item.classification()).isEqualTo("QUARANTINED");
            assertThat(item.detail()).contains("PERMANENT").doesNotContain("CONFLICT");
        });
    }

    private static DurableMessage durable(String sourceOwner, String targetOwner, String eventId,
            String type, String businessKey, String processInstanceId) {
        String payload = """
                {"schemaVersion":2,"tenantScope":"1","businessTable":"demo_leave_request","businessId":41,
                 "businessKey":"%s","round":1,"processInstanceId":"%s",
                 "commandId":"00000000-0000-0000-0000-000000000011",
                 "payload":{"operationId":"00000000-0000-0000-0000-000000000043"}}
                """.formatted(businessKey, processInstanceId);
        return DurableMessage.create(sourceOwner, targetOwner, eventId, type, 2, payload);
    }

    private static JdbcQuarantineStore.Snapshot quarantined(DurableMessage message, String reason) {
        String wire = new String(new DurableMessageWireCodec().encode(message),
                java.nio.charset.StandardCharsets.UTF_8);
        return new JdbcQuarantineStore.Snapshot(message.eventId(), wire, reason,
                Instant.parse("2026-09-22T00:01:00Z"));
    }

    private static UpmsRecoveryAuditStore.SavedRequest savedRequest(String requestId,
            String resourceType, String resourceId) {
        return new UpmsRecoveryAuditStore.SavedRequest("operator reason", "FAILED",
                new WorkflowRecoveryResultVO(requestId, "upms", resourceType, resourceId,
                        "FAILED", "FAILED", false, "UNCHANGED", "actor-7 result", Instant.now()));
    }
}
