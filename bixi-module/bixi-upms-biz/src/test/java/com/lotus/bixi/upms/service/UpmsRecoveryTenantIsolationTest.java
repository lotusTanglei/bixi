package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpmsRecoveryTenantIsolationTest {

    private static final String OWN_OUTBOX = "00000000-0000-0000-0000-000000000041";
    private static final String OTHER_OUTBOX = "00000000-0000-0000-0000-000000000042";
    private static final String OWN_INBOX = "00000000-0000-0000-0000-000000000043";
    private static final String OTHER_INBOX = "00000000-0000-0000-0000-000000000044";
    private static final String NUMERIC_SCOPE = "00000000-0000-0000-0000-000000000045";
    private static final String MISSING_SCOPE = "00000000-0000-0000-0000-000000000046";
    private static final String STRUCTURED_SCOPE = "00000000-0000-0000-0000-000000000047";

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void legacyInventoriesExposeOnlyTheCurrentTenant() {
        TenantContextHolder.set(41L);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        DurableMessage ownOutbox = message("upms", "workflow", OWN_OUTBOX, "41");
        DurableMessage otherOutbox = message("upms", "workflow", OTHER_OUTBOX, "42");
        DurableMessage ownInbox = message("workflow", "upms", OWN_INBOX, "41");
        DurableMessage otherInbox = message("workflow", "upms", OTHER_INBOX, "42");
        DurableMessage numeric = payloadMessage(NUMERIC_SCOPE, "{\"tenantScope\":41}");
        DurableMessage missing = payloadMessage(MISSING_SCOPE, "{}");
        DurableMessage structured = payloadMessage(STRUCTURED_SCOPE, "{\"tenantScope\":{}}");
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(outbox.listPage("upms", null, 200, 0L)).thenReturn(List.of(
                outbox(ownOutbox, now), outbox(otherOutbox, now), outbox(numeric, now),
                outbox(missing, now), outbox(structured, now)));
        when(inbox.listPage("upms", null, 200, 0L)).thenReturn(List.of(
                inbox(ownInbox, now), inbox(otherInbox, now)));
        when(quarantine.listPage(200, 0L)).thenReturn(List.of(
                quarantine(ownInbox, now), quarantine(otherInbox, now),
                new JdbcQuarantineStore.Snapshot("invalid-wire", "not-json", "WIRE_INVALID", now)));

        UpmsRecoveryService service = service(outbox, inbox, quarantine, mock(InboxExecutor.class));

        assertThat(service.listOutbox(null, 20)).extracting(row -> row.eventId()).containsExactly(OWN_OUTBOX);
        assertThat(service.listInbox(null, 20)).extracting(row -> row.eventId()).containsExactly(OWN_INBOX);
        assertThat(service.listQuarantine(20)).extracting(row -> row.evidenceId()).containsExactly(OWN_INBOX);
    }

    @Test
    void allTenantScopeKeepsLegacyInventoriesReadable() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        DurableMessage defaultOutbox = message("upms", "workflow", OWN_OUTBOX, "default");
        DurableMessage otherOutbox = message("upms", "workflow", OTHER_OUTBOX, "42");
        DurableMessage defaultInbox = message("workflow", "upms", OWN_INBOX, "1");
        DurableMessage otherInbox = message("workflow", "upms", OTHER_INBOX, "42");
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(outbox.list("upms", null, 20)).thenReturn(List.of(
                outbox(defaultOutbox, now), outbox(otherOutbox, now)));
        when(inbox.list("upms", null, 20)).thenReturn(List.of(
                inbox(defaultInbox, now), inbox(otherInbox, now)));
        when(quarantine.list(20)).thenReturn(List.of(
                quarantine(defaultInbox, now), quarantine(otherInbox, now)));

        UpmsRecoveryService service = service(outbox, inbox, quarantine, mock(InboxExecutor.class));

        assertThat(service.listOutbox(null, 20)).extracting(row -> row.eventId())
                .containsExactly(OWN_OUTBOX, OTHER_OUTBOX);
        assertThat(service.listInbox(null, 20)).extracting(row -> row.eventId())
                .containsExactly(OWN_INBOX, OTHER_INBOX);
        assertThat(service.listQuarantine(20)).extracting(row -> row.evidenceId())
                .containsExactly(OWN_INBOX, OTHER_INBOX);
    }

    @Test
    void tenantInventoryContinuesAfterOneFullForeignPage() {
        TenantContextHolder.set(41L);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        List<JdbcOutboxStore.AdminSnapshot> foreignPage = new java.util.ArrayList<>();
        for (int index = 0; index < 200; index++) {
            DurableMessage other = message("upms", "workflow",
                    "00000000-0000-0000-0002-%012d".formatted(index), "42");
            foreignPage.add(outbox(other, now));
        }
        DurableMessage own = message("upms", "workflow", OWN_OUTBOX, "41");
        when(outbox.listPage("upms", "FAILED", 200, 0L)).thenReturn(foreignPage);
        when(outbox.listPage("upms", "FAILED", 200, 200L)).thenReturn(List.of(outbox(own, now)));
        UpmsRecoveryService service = service(outbox, mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(InboxExecutor.class));

        assertThat(service.listOutbox("FAILED", 20)).extracting(row -> row.eventId())
                .containsExactly(OWN_OUTBOX);
        verify(outbox).listPage("upms", "FAILED", 200, 0L);
        verify(outbox).listPage("upms", "FAILED", 200, 200L);
    }

    @Test
    void reconciliationContinuesAfterOneFullPageOfForeignAndResolvedQuarantine() {
        TenantContextHolder.set(41L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        List<JdbcQuarantineStore.Snapshot> skippedPage = new java.util.ArrayList<>();
        for (int index = 0; index < 100; index++) {
            skippedPage.add(quarantine(message("workflow", "upms",
                    "00000000-0000-0000-0001-%012d".formatted(index), "42"), now));
        }
        for (int index = 0; index < 100; index++) {
            skippedPage.add(quarantine(message("workflow", "upms",
                    "00000000-0000-0000-0002-%012d".formatted(index), "41"), now));
        }
        DurableMessage resolved = message("workflow", "upms",
                "00000000-0000-0000-0002-000000000000", "41");
        DurableMessage unresolved = message("workflow", "upms", OWN_INBOX, "41");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(quarantine.listPage(200, 0L)).thenReturn(skippedPage);
        when(quarantine.listPage(200, 200L)).thenReturn(List.of(quarantine(unresolved, now)));
        when(inbox.find(eq("upms"), startsWith("00000000-0000-0000-0002-")))
                .thenReturn(new JdbcInboxStore.Snapshot(resolved, JdbcInboxStore.State.PROCESSED, 1));
        UpmsRecoveryService service = new UpmsRecoveryService(mock(JdbcOutboxStore.class), inbox,
                quarantine, mock(UpmsRecoveryAuditStore.class), access(7L, 41L), jdbc);

        assertThat(service.reconcile(20)).extracting(row -> row.eventId()).containsExactly(OWN_INBOX);
        verify(quarantine).listPage(200, 0L);
        verify(quarantine).listPage(200, 200L);
    }

    @Test
    void crossTenantLegacyMutationsNeverReachMutationStores() {
        TenantContextHolder.set(41L);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        DurableMessage otherOutbox = message("upms", "workflow", OTHER_OUTBOX, "42");
        DurableMessage otherInbox = message("workflow", "upms", OTHER_INBOX, "42");
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(outbox.find("upms", OTHER_OUTBOX)).thenReturn(outbox(otherOutbox, now));
        when(inbox.find("upms", OTHER_INBOX)).thenReturn(
                new JdbcInboxStore.Snapshot(otherInbox, JdbcInboxStore.State.FAILED, 1));
        when(quarantine.find("other-evidence")).thenReturn(quarantine(otherInbox, now));
        UpmsRecoveryService service = new UpmsRecoveryService(outbox, inbox, quarantine, audit,
                mock(UpmsRecoveryAccessService.class), executor);

        assertThatThrownBy(() -> service.retryOutbox(OTHER_OUTBOX, "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retryInbox(OTHER_INBOX, "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replayQuarantine("other-evidence", "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(outbox, never()).retry(anyString(), anyString(), any());
        verify(inbox, never()).retry(anyString(), anyString(), any());
        verifyNoInteractions(audit, executor);
    }

    @Test
    void allTenantScopeBlocksEveryRecoveryMutationBeforeStoreOrAuditAccess() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        UpmsRecoveryAuditStore audit = mock(UpmsRecoveryAuditStore.class);
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        UpmsRecoveryService service = new UpmsRecoveryService(outbox, inbox, quarantine, audit, access, executor);
        WorkflowRecoveryRequestDTO request = new WorkflowRecoveryRequestDTO(
                "00000000-0000-0000-0000-000000000099", "operator reason", "FAILED");

        assertReadOnly(() -> service.retryOutbox(OWN_OUTBOX, "operator reason"));
        assertReadOnly(() -> service.retryInbox(OWN_INBOX, "operator reason"));
        assertReadOnly(() -> service.retryLeaveEvent(OWN_OUTBOX, request));
        assertReadOnly(() -> service.retryLeaveCommand(OWN_OUTBOX, request));
        assertReadOnly(() -> service.reconcileLeaveBooking(OWN_OUTBOX, request));
        assertReadOnly(() -> service.replayQuarantine("evidence", "operator reason"));
        verifyNoInteractions(outbox, inbox, quarantine, audit, access, executor);
    }

    @Test
    void rawLeaveInventoriesAndReconciliationCarryTheCurrentTenantPredicate() {
        TenantContextHolder.set(41L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UpmsRecoveryAccessService access = access(7L, 41L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        UpmsRecoveryService service = service(jdbc, access);
        WorkflowRecoveryQueryDTO query = new WorkflowRecoveryQueryDTO();

        service.pageLeaveEvents(query);
        assertThatThrownBy(() -> service.leaveEvent(OWN_OUTBOX)).isInstanceOf(IllegalArgumentException.class);
        service.pageLeaveCommands(query);
        assertThatThrownBy(() -> service.leaveCommand(OWN_OUTBOX)).isInstanceOf(IllegalArgumentException.class);
        service.pageLeaveBusinessTasks(query);
        assertThatThrownBy(() -> service.leaveBusinessTask(OWN_OUTBOX)).isInstanceOf(IllegalArgumentException.class);
        service.reconcile(20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeast(7)).queryForList(sql.capture(), args.capture());
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(3)).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value)
                .containsAnyOf("tenant_scope", "tenant_id", "tenantScope"));
        assertThat(args.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value))
                .containsAnyOf(41L, "41"));
        List<String> bookingSql = java.util.stream.Stream.concat(
                        sql.getAllValues().stream(), countSql.getAllValues().stream())
                .filter(value -> value.contains("demo_leave_booking b"))
                .toList();
        assertThat(bookingSql).hasSize(4).allSatisfy(value -> assertThat(value)
                .contains("b.tenant_id = l.tenant_id"));
        List<String> commandSql = java.util.stream.Stream.concat(
                        sql.getAllValues().stream(), countSql.getAllValues().stream())
                .filter(value -> value.contains("demo_leave_command c"))
                .toList();
        assertThat(commandSql).hasSize(3).allSatisfy(value -> assertThat(value)
                .contains("CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END",
                        "= CAST(l.tenant_id AS CHAR)"));
        assertThat(sql.getAllValues().stream().filter(value -> value.contains("AS tenant_scope")))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(value)
                        .contains("JSON_TYPE(JSON_EXTRACT(payload_json, '$.tenantScope')) = 'STRING'"));
        String reconcileSql = sql.getAllValues().stream()
                .filter(value -> value.contains("SELECT l.id AS business_id"))
                .findFirst().orElseThrow();
        assertThat(reconcileSql)
                .contains("JSON_TYPE(JSON_EXTRACT(o.payload_json, '$.tenantScope')) = 'STRING'",
                        "= CAST(l.tenant_id AS CHAR)");
    }

    @Test
    void allTenantScopeOmitsTheCurrentTenantFromRawLeaveQueries() {
        TenantContextHolder.set(41L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UpmsRecoveryAccessService access = access(7L, 41L);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        UpmsRecoveryService service = service(jdbc, access);
        WorkflowRecoveryQueryDTO query = new WorkflowRecoveryQueryDTO();

        service.pageLeaveEvents(query);
        service.pageLeaveCommands(query);
        service.pageLeaveBusinessTasks(query);
        service.reconcile(20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, atLeast(4)).queryForList(sql.capture(), args.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value)
                .doesNotContain("tenant_scope = ?", "tenant_id = ?", "applicant_id = ?"));
        assertThat(args.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value))
                .doesNotContain(41L, "41", 7L));
    }

    private static void assertReadOnly(org.assertj.core.api.ThrowableAssert.ThrowingCallable mutation) {
        assertThatThrownBy(mutation).isInstanceOf(IllegalStateException.class)
                .hasMessage("all_tenants_read_only");
    }

    private static UpmsRecoveryService service(JdbcOutboxStore outbox, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, InboxExecutor executor) {
        return new UpmsRecoveryService(outbox, inbox, quarantine, mock(UpmsRecoveryAuditStore.class),
                mock(UpmsRecoveryAccessService.class), executor);
    }

    private static UpmsRecoveryService service(JdbcTemplate jdbc, UpmsRecoveryAccessService access) {
        return new UpmsRecoveryService(mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(UpmsRecoveryAuditStore.class), access, jdbc);
    }

    private static UpmsRecoveryAccessService access(long userId, long tenantId) {
        UpmsRecoveryAccessService access = mock(UpmsRecoveryAccessService.class);
        BixiUser actor = mock(BixiUser.class);
        when(actor.getId()).thenReturn(userId);
        when(actor.getTenantId()).thenReturn(tenantId);
        when(access.currentUser()).thenReturn(actor);
        return access;
    }

    private static DurableMessage message(String sourceOwner, String targetOwner, String eventId,
            String tenantScope) {
        return DurableMessage.create(sourceOwner, targetOwner, eventId, "WORKFLOW_TEST", 1,
                "{\"tenantScope\":\"" + tenantScope + "\"}");
    }

    private static DurableMessage payloadMessage(String eventId, String payload) {
        return DurableMessage.create("upms", "workflow", eventId, "WORKFLOW_TEST", 1, payload);
    }

    private static JdbcOutboxStore.AdminSnapshot outbox(DurableMessage message, Instant at) {
        return new JdbcOutboxStore.AdminSnapshot(message, "dedup", null, null, "FAILED", 1,
                at, null, null, at, null);
    }

    private static JdbcInboxStore.AdminSnapshot inbox(DurableMessage message, Instant at) {
        return new JdbcInboxStore.AdminSnapshot(message, "FAILED", 1, at, null, null, at, null);
    }

    private static JdbcQuarantineStore.Snapshot quarantine(DurableMessage message, Instant at) {
        String wire = new String(new DurableMessageWireCodec().encode(message), StandardCharsets.UTF_8);
        return new JdbcQuarantineStore.Snapshot(message.eventId(), wire, "PERMANENT", at);
    }
}
