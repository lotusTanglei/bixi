package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.job.api.JobQuery;
import org.flowable.job.api.TimerJobQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowRecoveryTenantIsolationTest {

    private static final String TENANT_EVENT = "00000000-0000-0000-0000-000000000041";
    private static final String OTHER_EVENT = "00000000-0000-0000-0000-000000000042";

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
        var own = message("workflow", "upms", TENANT_EVENT, "41");
        var other = message("workflow", "upms", OTHER_EVENT, "42");
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(outbox.listPage(eq("workflow"), isNull(), anyInt(), eq(0L))).thenReturn(List.of(
                new JdbcOutboxStore.AdminSnapshot(own, "dedup-own", null, null, "FAILED", 1,
                        now, null, null, now, null),
                new JdbcOutboxStore.AdminSnapshot(other, "dedup-other", null, null, "FAILED", 1,
                        now, null, null, now, null)));
        when(inbox.listPage(eq("workflow"), isNull(), anyInt(), eq(0L))).thenReturn(List.of(
                new JdbcInboxStore.AdminSnapshot(reverse(own), "FAILED", 1, now, null, null, now, null),
                new JdbcInboxStore.AdminSnapshot(reverse(other), "FAILED", 1, now, null, null, now, null)));
        when(quarantine.listPage(anyInt(), eq(0L))).thenReturn(List.of(
                quarantine(own, now), quarantine(other, now)));

        WorkflowRecoveryService service = service(outbox, inbox, quarantine, mock(InboxExecutor.class));

        assertThat(service.listOutbox(null, 20)).extracting(row -> row.eventId()).containsExactly(TENANT_EVENT);
        assertThat(service.listInbox(null, 20)).extracting(row -> row.eventId()).containsExactly(TENANT_EVENT);
        assertThat(service.listQuarantine(20)).extracting(row -> row.evidenceId()).containsExactly(TENANT_EVENT);
    }

    @Test
    void rawInventoryDetailsAndReconciliationCarryTheCurrentTenantPredicate() {
        TenantContextHolder.set(41L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        WorkflowRecoveryService service = service(mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), jdbc);
        WorkflowRecoveryQueryDTO query = new WorkflowRecoveryQueryDTO();

        service.pageEvents(query);
        service.pageCommands(query);
        service.pageBusinessTasks(query);
        assertThatThrownBy(() -> service.event(TENANT_EVENT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.command(TENANT_EVENT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.businessTask(TENANT_EVENT)).isInstanceOf(IllegalArgumentException.class);
        service.reconcile(20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(7)).queryForList(sql.capture(), args.capture());
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(3)).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value)
                .containsAnyOf("tenant_scope", "tenant_id"));
        assertThat(args.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value))
                .contains("41"));
        assertThat(countSql.getAllValues()).allSatisfy(value -> assertThat(value)
                .containsAnyOf("tenant_scope", "tenant_id"));
        assertThat(countArgs.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value))
                .contains("41"));
        List<String> commandSql = java.util.stream.Stream.concat(
                        sql.getAllValues().stream(), countSql.getAllValues().stream())
                .filter(value -> value.contains("FROM wf_command c")
                        && value.contains("LEFT JOIN wf_process_instance p"))
                .toList();
        assertThat(commandSql).hasSize(3).allSatisfy(value -> assertThat(value)
                .contains("CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END"
                        + " = CAST(p.tenant_id AS CHAR)"));
        assertThat(sql.getAllValues().stream().filter(value -> value.contains("AS tenant_scope")))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(value)
                        .contains("JSON_TYPE(JSON_EXTRACT(payload_json, '$.tenantScope')) = 'STRING'"));
        String reconcileSql = sql.getAllValues().stream()
                .filter(value -> value.contains("SELECT p.business_id"))
                .findFirst().orElseThrow();
        assertThat(reconcileSql)
                .contains("JSON_TYPE(JSON_EXTRACT(o.payload_json, '$.tenantScope')) = 'STRING'",
                        "CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END",
                        "CASE WHEN t.tenant_scope = 'default' THEN '1' ELSE t.tenant_scope END",
                        "CAST(p.tenant_id AS CHAR)");
    }

    @Test
    void allTenantScopeKeepsCrossTenantInventoryReadable() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        var own = message("workflow", "upms", TENANT_EVENT, "1");
        var other = message("workflow", "upms", OTHER_EVENT, "42");
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(outbox.list("workflow", null, 20)).thenReturn(List.of(
                new JdbcOutboxStore.AdminSnapshot(own, "dedup-own", null, null, "FAILED", 1,
                        now, null, null, now, null),
                new JdbcOutboxStore.AdminSnapshot(other, "dedup-other", null, null, "FAILED", 1,
                        now, null, null, now, null)));
        WorkflowRecoveryService service = service(outbox, mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(InboxExecutor.class));

        assertThat(service.listOutbox(null, 20)).extracting(row -> row.eventId())
                .containsExactly(TENANT_EVENT, OTHER_EVENT);
    }

    @Test
    void allTenantScopeKeepsRawInventoryDetailsAndReconciliationReadable() {
        TenantContextHolder.set(41L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        WorkflowRecoveryService service = service(mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), jdbc);
        WorkflowRecoveryQueryDTO query = new WorkflowRecoveryQueryDTO();

        service.pageEvents(query);
        service.pageCommands(query);
        service.pageBusinessTasks(query);
        assertThatThrownBy(() -> service.event(TENANT_EVENT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.command(TENANT_EVENT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.businessTask(TENANT_EVENT)).isInstanceOf(IllegalArgumentException.class);
        service.reconcile(20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(7)).queryForList(sql.capture(), args.capture());
        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgs = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(3)).queryForObject(countSql.capture(), eq(Long.class), countArgs.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value)
                .doesNotContain("tenant_scope = ?", "tenant_id = ?"));
        assertThat(args.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value))
                .doesNotContain("41"));
        assertThat(countSql.getAllValues()).allSatisfy(value -> assertThat(value)
                .doesNotContain("tenant_scope = ?", "tenant_id = ?"));
        assertThat(countArgs.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value))
                .doesNotContain("41"));
        List<String> commandSql = java.util.stream.Stream.concat(
                        sql.getAllValues().stream(), countSql.getAllValues().stream())
                .filter(value -> value.contains("FROM wf_command c")
                        && value.contains("LEFT JOIN wf_process_instance p"))
                .toList();
        assertThat(commandSql).hasSize(3).allSatisfy(value -> assertThat(value)
                .contains("CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END"
                        + " = CAST(p.tenant_id AS CHAR)"));
    }

    @Test
    void tenantInventoryIsNotStarvedByForeignRowsAheadOfItsLimit() {
        TenantContextHolder.set(41L);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        List<JdbcOutboxStore.AdminSnapshot> foreignPage = new java.util.ArrayList<>();
        for (int index = 0; index < 200; index++) {
            DurableMessage other = message("workflow", "upms",
                    "00000000-0000-0000-0001-%012d".formatted(index), "42");
            foreignPage.add(new JdbcOutboxStore.AdminSnapshot(other, "dedup-" + index, null, null,
                    "FAILED", 1, now, null, null, now, null));
        }
        DurableMessage own = message("workflow", "upms", TENANT_EVENT, "41");
        JdbcOutboxStore.AdminSnapshot ownRow = new JdbcOutboxStore.AdminSnapshot(
                own, "dedup-own", null, null, "FAILED", 1, now, null, null, now, null);
        when(outbox.listPage("workflow", "FAILED", 200, 0L)).thenReturn(foreignPage);
        when(outbox.listPage("workflow", "FAILED", 200, 200L)).thenReturn(List.of(ownRow));
        WorkflowRecoveryService service = service(outbox, mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), mock(InboxExecutor.class));

        assertThat(service.listOutbox("FAILED", 20)).extracting(row -> row.eventId())
                .containsExactly(TENANT_EVENT);
        verify(outbox).listPage("workflow", "FAILED", 200, 0L);
        verify(outbox).listPage("workflow", "FAILED", 200, 200L);
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
            skippedPage.add(quarantine(message("upms", "workflow",
                    "00000000-0000-0000-0001-%012d".formatted(index), "42"), now));
        }
        for (int index = 0; index < 100; index++) {
            skippedPage.add(quarantine(message("upms", "workflow",
                    "00000000-0000-0000-0002-%012d".formatted(index), "41"), now));
        }
        DurableMessage resolved = message("upms", "workflow",
                "00000000-0000-0000-0002-000000000000", "41");
        DurableMessage unresolved = message("upms", "workflow", TENANT_EVENT, "41");
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(quarantine.listPage(200, 0L)).thenReturn(skippedPage);
        when(quarantine.listPage(200, 200L)).thenReturn(List.of(quarantine(unresolved, now)));
        when(inbox.find(eq("workflow"), startsWith("00000000-0000-0000-0002-")))
                .thenReturn(new JdbcInboxStore.Snapshot(resolved, JdbcInboxStore.State.PROCESSED, 1));
        WorkflowRecoveryService service = service(mock(JdbcOutboxStore.class), inbox, quarantine, jdbc);

        assertThat(service.reconcile(20)).extracting(row -> row.eventId()).containsExactly(TENANT_EVENT);
        verify(quarantine).listPage(200, 0L);
        verify(quarantine).listPage(200, 200L);
    }

    @Test
    void allTenantScopeBlocksEveryRecoveryMutationBeforeStoreOrAuditAccess() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        WorkflowRecoveryService service = new WorkflowRecoveryService(outbox, inbox, quarantine, audit,
                mock(WorkflowAccessService.class), executor);
        WorkflowRecoveryRequestDTO request = new WorkflowRecoveryRequestDTO(
                "00000000-0000-0000-0000-000000000099", "operator reason", "FAILED");

        assertThatThrownBy(() -> service.retryOutbox(TENANT_EVENT, "operator reason"))
                .isInstanceOf(IllegalStateException.class).hasMessage("all_tenants_read_only");
        assertThatThrownBy(() -> service.retryInbox(TENANT_EVENT, "operator reason"))
                .isInstanceOf(IllegalStateException.class).hasMessage("all_tenants_read_only");
        assertThatThrownBy(() -> service.retryEvent(TENANT_EVENT, request))
                .isInstanceOf(IllegalStateException.class).hasMessage("all_tenants_read_only");
        assertThatThrownBy(() -> service.reconcileBusinessTask(TENANT_EVENT, request))
                .isInstanceOf(IllegalStateException.class).hasMessage("all_tenants_read_only");
        assertThatThrownBy(() -> service.replayQuarantine("evidence", "operator reason"))
                .isInstanceOf(IllegalStateException.class).hasMessage("all_tenants_read_only");
        verifyNoInteractions(outbox, inbox, quarantine, audit, executor);
    }

    @Test
    void crossTenantLegacyRetriesAndQuarantineReplayNeverReachMutationStores() {
        TenantContextHolder.set(41L);
        JdbcOutboxStore outbox = mock(JdbcOutboxStore.class);
        JdbcInboxStore inbox = mock(JdbcInboxStore.class);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        var otherOutbox = message("workflow", "upms", OTHER_EVENT, "42");
        var otherInbox = reverse(otherOutbox);
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(outbox.find("workflow", OTHER_EVENT)).thenReturn(
                new JdbcOutboxStore.AdminSnapshot(otherOutbox, "dedup-other", null, null, "FAILED", 1,
                        now, null, null, now, null));
        when(inbox.find("workflow", OTHER_EVENT)).thenReturn(
                new JdbcInboxStore.Snapshot(otherInbox, JdbcInboxStore.State.FAILED, 1));
        when(quarantine.find("other-evidence")).thenReturn(quarantine(otherInbox, now));
        WorkflowRecoveryService service = service(outbox, inbox, quarantine, executor);

        assertThatThrownBy(() -> service.retryOutbox(OTHER_EVENT, "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retryInbox(OTHER_EVENT, "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replayQuarantine("other-evidence", "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(outbox, never()).retry(anyString(), anyString(), any());
        verify(inbox, never()).retry(anyString(), anyString(), any());
        verifyNoInteractions(executor);
    }

    @Test
    void ordinaryTenantRejectsUnscopedAndMalformedQuarantineBeforeSideEffects() {
        TenantContextHolder.set(41L);
        JdbcQuarantineStore quarantine = mock(JdbcQuarantineStore.class);
        WorkflowRecoveryAuditStore audit = mock(WorkflowRecoveryAuditStore.class);
        WorkflowAccessService access = mock(WorkflowAccessService.class);
        InboxExecutor executor = mock(InboxExecutor.class);
        DurableMessage missingScope = DurableMessage.create("upms", "workflow", TENANT_EVENT,
                "WORKFLOW_TEST", 1, "{}");
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        when(quarantine.find("missing-scope")).thenReturn(quarantine(missingScope, now));
        when(quarantine.find("malformed-wire")).thenReturn(
                new JdbcQuarantineStore.Snapshot("malformed-wire", "{not-json", "PERMANENT", now));
        WorkflowRecoveryService service = new WorkflowRecoveryService(mock(JdbcOutboxStore.class),
                mock(JdbcInboxStore.class), quarantine, audit, access, executor);

        assertThatThrownBy(() -> service.replayQuarantine("missing-scope", "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replayQuarantine("malformed-wire", "operator reason"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(audit, access, executor);
    }

    @Test
    void diagnosticsScopesDurableCountsAndRecentFailureToTheCurrentTenant() {
        TenantContextHolder.set(41L);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending_count", 0L);
        stats.put("failed_count", 0L);
        stats.put("oldest_waiting_at", null);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(stats);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        JobQuery jobs = mock(JobQuery.class, RETURNS_SELF);
        TimerJobQuery timers = mock(TimerJobQuery.class, RETURNS_SELF);
        DeadLetterJobQuery deadLetters = mock(DeadLetterJobQuery.class, RETURNS_SELF);
        ProcessEngine engine = engine(jobs, timers, deadLetters);
        WorkflowRecoveryService service = service(mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), jdbc, engine);

        service.diagnostics();

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).queryForMap(sql.capture(), args.capture());
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(sql.getAllValues()).allSatisfy(value -> assertThat(value)
                .contains("tenantScope",
                        "JSON_TYPE(JSON_EXTRACT(payload_json, '$.tenantScope')) = 'STRING'"));
        assertThat(args.getAllValues()).allSatisfy(value -> assertThat(java.util.Arrays.asList(value)).contains("41"));
        InOrder jobOrder = inOrder(jobs);
        jobOrder.verify(jobs).jobTenantId("41");
        jobOrder.verify(jobs).count();
        InOrder timerOrder = inOrder(timers);
        timerOrder.verify(timers).jobTenantId("41");
        timerOrder.verify(timers).count();
        InOrder deadLetterOrder = inOrder(deadLetters);
        deadLetterOrder.verify(deadLetters).jobTenantId("41");
        deadLetterOrder.verify(deadLetters).count();
    }

    @Test
    void allTenantDiagnosticsCountEveryFlowableTenantWithoutTenantFilters() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Map<String, Object> stats = new HashMap<>();
        stats.put("pending_count", 0L);
        stats.put("failed_count", 0L);
        stats.put("oldest_waiting_at", null);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(stats);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        JobQuery jobs = mock(JobQuery.class, RETURNS_SELF);
        TimerJobQuery timers = mock(TimerJobQuery.class, RETURNS_SELF);
        DeadLetterJobQuery deadLetters = mock(DeadLetterJobQuery.class, RETURNS_SELF);
        WorkflowRecoveryService service = service(mock(JdbcOutboxStore.class), mock(JdbcInboxStore.class),
                mock(JdbcQuarantineStore.class), jdbc, engine(jobs, timers, deadLetters));

        service.diagnostics();

        verify(jobs).count();
        verify(jobs, never()).jobTenantId(anyString());
        verify(timers).count();
        verify(timers, never()).jobTenantId(anyString());
        verify(deadLetters).count();
        verify(deadLetters, never()).jobTenantId(anyString());
    }

    private static WorkflowRecoveryService service(JdbcOutboxStore outbox, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, InboxExecutor executor) {
        return new WorkflowRecoveryService(outbox, inbox, quarantine, mock(WorkflowRecoveryAuditStore.class),
                mock(WorkflowAccessService.class), executor);
    }

    private static WorkflowRecoveryService service(JdbcOutboxStore outbox, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, JdbcTemplate jdbc) {
        return new WorkflowRecoveryService(outbox, inbox, quarantine, mock(WorkflowRecoveryAuditStore.class),
                mock(WorkflowAccessService.class), jdbc);
    }

    private static WorkflowRecoveryService service(JdbcOutboxStore outbox, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, JdbcTemplate jdbc, ProcessEngine engine) {
        return new WorkflowRecoveryService(outbox, inbox, quarantine, mock(WorkflowRecoveryAuditStore.class),
                mock(WorkflowAccessService.class), jdbc, engine);
    }

    private static DurableMessage message(String source, String target, String eventId, String tenantScope) {
        return DurableMessage.create(source, target, eventId, "WORKFLOW_TEST", 1,
                "{\"tenantScope\":\"" + tenantScope + "\"}");
    }

    private static DurableMessage reverse(DurableMessage message) {
        return DurableMessage.create(message.targetOwner(), message.sourceOwner(), message.eventId(),
                message.type(), message.schemaVersion(), message.payloadJson());
    }

    private static JdbcQuarantineStore.Snapshot quarantine(DurableMessage message, Instant at) {
        String wire = new String(new DurableMessageWireCodec().encode(message), StandardCharsets.UTF_8);
        return new JdbcQuarantineStore.Snapshot(message.eventId(), wire, "PERMANENT", at);
    }

    private static ProcessEngine engine() {
        return engine(mock(JobQuery.class), mock(TimerJobQuery.class), mock(DeadLetterJobQuery.class));
    }

    private static ProcessEngine engine(JobQuery jobs, TimerJobQuery timers,
            DeadLetterJobQuery deadLetters) {
        ProcessEngine engine = mock(ProcessEngine.class);
        ProcessEngineConfigurationImpl configuration = mock(ProcessEngineConfigurationImpl.class);
        var async = mock(org.flowable.job.service.impl.asyncexecutor.AsyncExecutor.class);
        ManagementService management = mock(ManagementService.class);
        when(engine.getProcessEngineConfiguration()).thenReturn(configuration);
        when(engine.getManagementService()).thenReturn(management);
        when(configuration.getAsyncExecutor()).thenReturn(async);
        when(management.createJobQuery()).thenReturn(jobs);
        when(management.createTimerJobQuery()).thenReturn(timers);
        when(management.createDeadLetterJobQuery()).thenReturn(deadLetters);
        return engine;
    }
}
