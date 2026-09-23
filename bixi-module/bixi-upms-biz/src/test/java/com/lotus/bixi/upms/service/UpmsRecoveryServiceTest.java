package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryActionVO;
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
        when(inbox.retry("upms", eventId)).thenReturn(true);

        UpmsRecoveryService service = new UpmsRecoveryService(outbox, inbox, quarantine, audit, access,
                mock(InboxExecutor.class));
        WorkflowRecoveryActionVO result = service.retryInbox(eventId, "人工恢复");

        assertThat(result.changed()).isTrue();
        assertThat(result.direction()).isEqualTo("INBOX");
        verify(audit).record(7L, "INBOX_RETRY", "upms", eventId, null, true, "人工恢复");
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
                "00000000-0000-0000-0000-000000000008", "WORKFLOW_COMPLETED", 1, "{\"secret\":true}");
        Instant now = Instant.parse("2026-09-22T00:00:00Z");
        when(outbox.list("upms", "FAILED", 20)).thenReturn(List.of(new JdbcOutboxStore.AdminSnapshot(
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
        verify(outbox).list("upms", "FAILED", 20);
    }

    @Test
    void replayQuarantineRecordsFailedAuditWhenTheWireBodyIsInvalid() {
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(7L);
        when(access.currentUser()).thenReturn(actor);
        when(quarantine.find("evidence-invalid")).thenReturn(new JdbcQuarantineStore.Snapshot(
                "evidence-invalid", "not-json", "WIRE_FORMAT_INVALID", Instant.now()));

        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), quarantine, audit, access, executor);

        assertThatThrownBy(() -> service.replayQuarantine("evidence-invalid", "确认坏消息不可重放"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(executor);
        verify(audit).record(7L, "QUARANTINE_REPLAY", "upms", null,
                "evidence-invalid", false, "确认坏消息不可重放");
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
        when(jdbc.queryForList(anyString(), eq(20))).thenReturn(List.of(row));

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
        when(jdbc.queryForList(anyString(), eq(20))).thenReturn(List.of(row));

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
        verify(jdbc).queryForList(sql.capture(), eq(20));
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
        when(jdbc.queryForList(anyString(), eq(20))).thenReturn(List.of(row));

        String unresolvedId = "00000000-0000-0000-0000-000000000041";
        DurableMessage unresolved = durable("workflow", "upms", unresolvedId,
                "WORKFLOW_BUSINESS_TASK_REQUESTED", "demo_leave:41:1", "process-41");
        String resolvedId = "00000000-0000-0000-0000-000000000042";
        DurableMessage resolved = durable("workflow", "upms", resolvedId,
                "WORKFLOW_BUSINESS_TASK_REQUESTED", "demo_leave:42:1", "process-42");
        when(quarantine.list(20)).thenReturn(List.of(
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
                {"schemaVersion":2,"businessTable":"demo_leave_request","businessId":41,
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
}
