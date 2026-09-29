package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.RecoveryTenantScope;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
import com.lotus.bixi.workflow.api.event.WorkflowRecoveryMetadata;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryBusinessTaskVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryCommandVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryEventVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryPageVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryResultVO;
import com.lotus.bixi.workflow.api.vo.WorkflowInboxSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowOutboxSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowQuarantineSnapshotVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryActionVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryQuarantine;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryReconciliationVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRecoveryReplayVO;
import com.lotus.bixi.workflow.api.vo.WorkflowRuntimeDiagnosticsVO;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

/** Operator-facing recovery operations for the workflow owner. */
@Service
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
public class WorkflowRecoveryService {
    public static final String OWNER = "workflow";

    private final JdbcOutboxStore outbox;
    private final JdbcInboxStore inbox;
    private final JdbcQuarantineStore quarantine;
    private final WorkflowRecoveryAuditStore audit;
    private final WorkflowAccessService access;
    private final JdbcTemplate jdbc;
    private final InboxExecutor executor;
    private final ProcessEngine processEngine;

    public WorkflowRecoveryService(@Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("workflowInboxStore") JdbcInboxStore inbox,
            @Qualifier("workflowQuarantineStore") JdbcQuarantineStore quarantine,
            WorkflowRecoveryAuditStore audit, WorkflowAccessService access) {
        this(outbox, inbox, quarantine, audit, access, (JdbcTemplate) null, (InboxExecutor) null,
                (ProcessEngine) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public WorkflowRecoveryService(@Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("workflowInboxStore") JdbcInboxStore inbox,
            @Qualifier("workflowQuarantineStore") JdbcQuarantineStore quarantine,
            WorkflowRecoveryAuditStore audit, WorkflowAccessService access, javax.sql.DataSource dataSource,
            @Qualifier("workflowInboxExecutor") InboxExecutor executor, ProcessEngine processEngine) {
        this(outbox, inbox, quarantine, audit, access,
                dataSource == null ? null : new JdbcTemplate(dataSource), executor, processEngine);
    }

    WorkflowRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            WorkflowRecoveryAuditStore audit, WorkflowAccessService access, JdbcTemplate jdbc) {
        this(outbox, inbox, quarantine, audit, access, jdbc, null, null);
    }

    WorkflowRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            WorkflowRecoveryAuditStore audit, WorkflowAccessService access, JdbcTemplate jdbc,
            ProcessEngine processEngine) {
        this(outbox, inbox, quarantine, audit, access, jdbc, null, processEngine);
    }

    public WorkflowRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            WorkflowRecoveryAuditStore audit, WorkflowAccessService access, InboxExecutor executor) {
        this(outbox, inbox, quarantine, audit, access, (JdbcTemplate) null, executor, (ProcessEngine) null);
    }

    private WorkflowRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            WorkflowRecoveryAuditStore audit, WorkflowAccessService access, JdbcTemplate jdbc,
            InboxExecutor executor, ProcessEngine processEngine) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.quarantine = quarantine;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
        this.executor = executor;
        this.processEngine = processEngine;
    }

    public List<WorkflowOutboxSnapshotVO> listOutbox(String status, int limit) {
        requireLimit(limit);
        String normalized = normalizeStatus(status);
        List<JdbcOutboxStore.AdminSnapshot> snapshots = TenantContextHolder.isAllTenantsReadOnly()
                ? outbox.list(OWNER, normalized, limit)
                : visibleInventory(limit, offset -> outbox.listPage(OWNER, normalized, 200, offset),
                        JdbcOutboxStore.AdminSnapshot::message);
        return snapshots.stream().map(snapshot ->
                new WorkflowOutboxSnapshotVO(snapshot.message().sourceOwner(), snapshot.message().eventId(),
                        snapshot.message().targetOwner(), snapshot.message().type(), snapshot.message().schemaVersion(),
                        snapshot.message().payloadHash(), snapshot.status(), snapshot.attempts(),
                        snapshot.nextAttemptAt(), snapshot.leaseUntil(), snapshot.lastError(),
                        snapshot.createdAt(), snapshot.deliveredAt())).toList();
    }

    public List<WorkflowInboxSnapshotVO> listInbox(String status, int limit) {
        requireLimit(limit);
        String normalized = normalizeStatus(status);
        List<JdbcInboxStore.AdminSnapshot> snapshots = TenantContextHolder.isAllTenantsReadOnly()
                ? inbox.list(OWNER, normalized, limit)
                : visibleInventory(limit, offset -> inbox.listPage(OWNER, normalized, 200, offset),
                        JdbcInboxStore.AdminSnapshot::message);
        return snapshots.stream().map(snapshot ->
                new WorkflowInboxSnapshotVO(snapshot.message().targetOwner(), snapshot.message().eventId(),
                        snapshot.message().sourceOwner(), snapshot.message().type(), snapshot.message().schemaVersion(),
                        snapshot.message().payloadHash(), snapshot.status(), snapshot.attempts(),
                        snapshot.nextAttemptAt(), snapshot.leaseUntil(), snapshot.lastError(),
                        snapshot.receivedAt(), snapshot.processedAt())).toList();
    }

    public List<WorkflowQuarantineSnapshotVO> listQuarantine(int limit) {
        requireLimit(limit);
        List<JdbcQuarantineStore.Snapshot> snapshots = TenantContextHolder.isAllTenantsReadOnly()
                ? quarantine.list(limit)
                : visibleInventory(limit, offset -> quarantine.listPage(200, offset),
                        WorkflowRecoveryService::quarantineMessage);
        return snapshots.stream().map(snapshot -> new WorkflowQuarantineSnapshotVO(
                snapshot.evidenceId(), null, snapshot.reason(), snapshot.quarantinedAt())).toList();
    }

    public WorkflowRecoveryPageVO<WorkflowRecoveryEventVO> pageEvents(WorkflowRecoveryQueryDTO query) {
        requireJdbc();
        requireQuery(query);
        long offset = pageOffset(query);
        String owner = requireOwner(query.getOwner());
        String base = eventUnion();
        String where = " WHERE event_owner = ?" + tenantPredicate("tenant_scope", true)
                + " AND (? IS NULL OR direction = ?)"
                + " AND (? IS NULL OR event_type = ?) AND (? IS NULL OR event_status = ?)"
                + " AND (? IS NULL OR created_at >= ?) AND (? IS NULL OR created_at <= ?)"
                + " AND (? IS NULL OR business_id = ?)";
        List<Object> args = eventArgs(owner, query);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + base + ") e" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        List<WorkflowRecoveryEventVO> rows = jdbc.queryForList("SELECT * FROM (" + base + ") e" + where
                + " ORDER BY created_at DESC, event_id DESC LIMIT ? OFFSET ?", pageArgs.toArray())
                .stream().map(WorkflowRecoveryService::toEvent).toList();
        return WorkflowRecoveryPageVO.of(rows, total == null ? 0 : total,
                query.getCurrent(), query.getSize());
    }

    public WorkflowRecoveryEventVO event(String eventId) {
        requireJdbc();
        requireEventId(eventId);
        List<Object> args = new ArrayList<>(List.of(OWNER, eventId));
        addTenantArg(args);
        List<WorkflowRecoveryEventVO> rows = jdbc.queryForList("SELECT * FROM (" + eventUnion()
                + ") e WHERE event_owner = ? AND event_id = ?" + tenantPredicate("tenant_scope", true)
                + " ORDER BY created_at DESC LIMIT 1", args.toArray())
                .stream().map(WorkflowRecoveryService::toEvent).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("恢复事件不存在");
        return rows.get(0);
    }

    public WorkflowRecoveryPageVO<WorkflowRecoveryCommandVO> pageCommands(WorkflowRecoveryQueryDTO query) {
        requireJdbc();
        requireQuery(query);
        long offset = pageOffset(query);
        requireOwner(query.getOwner());
        String where = " WHERE c.source_owner = 'workflow'" + tenantPredicate("c.tenant_scope", true)
                + " AND (? IS NULL OR c.operation = ?)"
                + " AND (? IS NULL OR c.status = ?) AND (? IS NULL OR c.created_at >= ?)"
                + " AND (? IS NULL OR c.created_at <= ?) AND (? IS NULL OR p.business_id = ?)";
        List<Object> args = commonArgs(query);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wf_command c LEFT JOIN wf_process_instance p"
                + " ON p.process_instance_id = c.process_instance_id AND "
                + normalizedTenantColumn("c.tenant_scope") + " = CAST(p.tenant_id AS CHAR)"
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        List<WorkflowRecoveryCommandVO> rows = jdbc.queryForList("""
                SELECT c.id AS command_id, c.request_id, c.operation, c.resource_id, c.status,
                       c.process_instance_id, c.actor_id, c.result_code AS error,
                       c.created_at, c.completed_at, p.business_id, p.business_key
                  FROM wf_command c
                  LEFT JOIN wf_process_instance p ON p.process_instance_id = c.process_instance_id
                   AND CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END = CAST(p.tenant_id AS CHAR)
                """ + where + " ORDER BY c.created_at DESC, c.id DESC LIMIT ? OFFSET ?", pageArgs.toArray())
                .stream().map(WorkflowRecoveryService::toCommand).toList();
        return WorkflowRecoveryPageVO.of(rows, total == null ? 0 : total,
                query.getCurrent(), query.getSize());
    }

    public WorkflowRecoveryCommandVO command(String commandId) {
        requireJdbc();
        requireEventId(commandId);
        List<Object> args = new ArrayList<>(List.of(commandId));
        addTenantArg(args);
        List<WorkflowRecoveryCommandVO> rows = jdbc.queryForList("""
                SELECT c.id AS command_id, c.request_id, c.operation, c.resource_id, c.status,
                       c.process_instance_id, c.actor_id, c.result_code AS error,
                       p.business_id, p.business_key, c.created_at, c.completed_at
                  FROM wf_command c
                  LEFT JOIN wf_process_instance p ON p.process_instance_id = c.process_instance_id
                   AND CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END = CAST(p.tenant_id AS CHAR)
                 WHERE c.id = ? AND c.source_owner = 'workflow'
                """ + tenantPredicate("c.tenant_scope", true), args.toArray())
                .stream().map(WorkflowRecoveryService::toCommand).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("工作流命令不存在");
        return rows.get(0);
    }

    public WorkflowRecoveryPageVO<WorkflowRecoveryBusinessTaskVO> pageBusinessTasks(
            WorkflowRecoveryQueryDTO query) {
        requireJdbc();
        requireQuery(query);
        long offset = pageOffset(query);
        requireOwner(query.getOwner());
        String where = " WHERE 1 = 1" + tenantPredicate("t.tenant_scope", true)
                + " AND (? IS NULL OR t.status = ?) AND (? IS NULL OR t.created_at >= ?)"
                + " AND (? IS NULL OR t.created_at <= ?) AND (? IS NULL OR t.business_id = ?)";
        List<Object> args = taskArgs(query);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wf_business_task t" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        List<WorkflowRecoveryBusinessTaskVO> rows = jdbc.queryForList("""
                SELECT t.operation_id, t.process_instance_id, t.business_table, t.business_id,
                       t.business_round AS round, t.status, t.result_event_id, t.compensation_id,
                       t.deadline, t.last_error, t.created_at, t.updated_at
                  FROM wf_business_task t
                """ + where + " ORDER BY t.created_at DESC, t.operation_id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray()).stream().map(WorkflowRecoveryService::toBusinessTask).toList();
        return WorkflowRecoveryPageVO.of(rows, total == null ? 0 : total,
                query.getCurrent(), query.getSize());
    }

    public WorkflowRecoveryBusinessTaskVO businessTask(String operationId) {
        requireJdbc();
        requireEventId(operationId);
        List<Object> args = new ArrayList<>(List.of(operationId));
        addTenantArg(args);
        List<WorkflowRecoveryBusinessTaskVO> rows = jdbc.queryForList("""
                SELECT operation_id, process_instance_id, business_table, business_id,
                       business_round AS round, status, result_event_id, compensation_id,
                       deadline, last_error, created_at, updated_at
                  FROM wf_business_task WHERE operation_id = ?
                """ + tenantPredicate("tenant_scope", true), args.toArray())
                .stream().map(WorkflowRecoveryService::toBusinessTask).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("工作流业务任务不存在");
        return rows.get(0);
    }

    public WorkflowRecoveryResultVO reconcileBusinessTask(String operationId,
            WorkflowRecoveryRequestDTO request) {
        RecoveryTenantScope.requireWritable();
        requireRequest(request);
        WorkflowRecoveryAuditStore.SavedRequest replay = audit.findRequest(OWNER,
                "BUSINESS_TASK_RECONCILE", request.requestId());
        if (replay != null) {
            requireMatchingWinner(request, OWNER, "BUSINESS_TASK", operationId, replay);
            return replay.result();
        }
        WorkflowRecoveryBusinessTaskVO task = businessTask(operationId);
        if (!request.expectedStatus().equals(task.status())) {
            throw new IllegalStateException("工作流任务状态与 expectedStatus 不一致");
        }
        String outcome = isBusinessTaskTerminal(task.status()) ? "MATCHED" : "NO_SAFE_REPAIR";
        String detail = "MATCHED".equals(outcome)
                ? "工作流任务已处于稳定状态" : "未发现可由 Workflow owner 安全修复的状态转换";
        WorkflowRecoveryResultVO value = new WorkflowRecoveryResultVO(request.requestId(), OWNER, "BUSINESS_TASK", operationId,
                task.status(), task.status(), false, outcome, detail, Instant.now());
        BixiUser actor = access.currentUser();
        try {
            audit.recordRequest(actor.getId(), "BUSINESS_TASK_RECONCILE", OWNER, operationId,
                    request.reason(), request.expectedStatus(), value);
        }
        catch (org.springframework.dao.DuplicateKeyException race) {
            WorkflowRecoveryAuditStore.SavedRequest winner = audit.findRequest(OWNER,
                    "BUSINESS_TASK_RECONCILE", request.requestId());
            if (winner != null) {
                requireMatchingWinner(request, OWNER, "BUSINESS_TASK", operationId, winner);
                return winner.result();
            }
            throw race;
        }
        return value;
    }

    public WorkflowRecoveryResultVO retryEvent(String eventId, WorkflowRecoveryRequestDTO request) {
        RecoveryTenantScope.requireWritable();
        requireRequest(request);
        WorkflowRecoveryAuditStore.SavedRequest replay = audit.findRequest(OWNER, "EVENT_RETRY", request.requestId());
        if (replay != null) {
            requireMatchingWinner(request, OWNER, null, eventId, replay);
            return replay.result();
        }
        WorkflowRecoveryEventVO saved = event(eventId);
        if (!request.expectedStatus().equals(saved.status())) {
            throw new IllegalStateException("工作流事件状态与 expectedStatus 不一致");
        }
        if ("IN_FLIGHT".equals(saved.status()) && saved.leaseUntil() != null
                && saved.leaseUntil().isAfter(Instant.now())) {
            throw new IllegalStateException("事件仍由活跃租约持有，不能抢占");
        }
        if (!"FAILED".equals(saved.status())) {
            throw new IllegalStateException("仅 FAILED 事件可以人工重试");
        }
        BixiUser actor = access.currentUser();
        java.util.concurrent.atomic.AtomicReference<WorkflowRecoveryResultVO> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Consumer<Boolean> callback = changed -> {
            String current = changed ? ("OUTBOX".equals(saved.direction()) ? "PENDING" : "RECEIVED") : saved.status();
            WorkflowRecoveryResultVO value = new WorkflowRecoveryResultVO(request.requestId(), OWNER,
                    saved.direction(), eventId, saved.status(), current, changed,
                    changed ? "RETRIED" : "UNCHANGED",
                    changed ? "事件已重新进入 owner 队列" : "事件状态已变化，未执行重试", Instant.now());
            audit.recordRequest(actor.getId(), "EVENT_RETRY", OWNER, eventId, request.reason(),
                    request.expectedStatus(), value);
            result.set(value);
        };
        try {
            if ("OUTBOX".equals(saved.direction())) outbox.retry(OWNER, eventId, callback);
            else inbox.retry(OWNER, eventId, callback);
        }
        catch (org.springframework.dao.DuplicateKeyException race) {
            WorkflowRecoveryAuditStore.SavedRequest winner = audit.findRequest(OWNER, "EVENT_RETRY",
                    request.requestId());
            if (winner != null) {
                requireMatchingWinner(request, OWNER, saved.direction(), eventId, winner);
                return winner.result();
            }
            throw race;
        }
        return result.get();
    }

    /**
     * Reads actual engine and durable-queue state for recovery diagnosis. Payload columns are
     * deliberately absent from every query in this method.
     */
    public WorkflowRuntimeDiagnosticsVO diagnostics() {
        if (jdbc == null || processEngine == null) {
            throw new IllegalStateException("Runtime diagnostics requires workflow engine and DataSource");
        }
        ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();
        if (!(configuration instanceof ProcessEngineConfigurationImpl runtimeConfiguration)) {
            throw new IllegalStateException("Workflow runtime configuration does not expose async settings");
        }
        AsyncExecutor asyncExecutor = configuration.getAsyncExecutor();
        if (asyncExecutor == null) {
            throw new IllegalStateException("Workflow async executor is not configured");
        }
        DurableStats outboxStats = durableStats("reliable_outbox", "source_owner", "created_at",
                "'PENDING', 'IN_FLIGHT'", OWNER);
        DurableStats inboxStats = durableStats("reliable_inbox", "target_owner", "received_at",
                "'RECEIVED', 'IN_FLIGHT'", OWNER);
        RecentFailure recentFailure = recentFailure();
        ManagementService management = processEngine.getManagementService();
        org.flowable.job.api.JobQuery jobs = management.createJobQuery();
        org.flowable.job.api.TimerJobQuery timers = management.createTimerJobQuery();
        org.flowable.job.api.DeadLetterJobQuery deadLetters = management.createDeadLetterJobQuery();
        if (!TenantContextHolder.isAllTenantsReadOnly()) {
            String tenantScope = RecoveryTenantScope.currentScope();
            jobs.jobTenantId(tenantScope);
            timers.jobTenantId(tenantScope);
            deadLetters.jobTenantId(tenantScope);
        }
        return new WorkflowRuntimeDiagnosticsVO(
                asyncExecutor.getLockOwner(),
                configuration.isAsyncExecutorActivate(),
                asyncExecutor.isAutoActivate(),
                asyncExecutor.isActive(),
                asyncExecutor.getAsyncJobLockTimeInMillis(),
                asyncExecutor.getTimerLockTimeInMillis(),
                asyncExecutor.getResetExpiredJobsInterval(),
                asyncExecutor.getDefaultAsyncJobAcquireWaitTimeInMillis(),
                asyncExecutor.getDefaultTimerJobAcquireWaitTimeInMillis(),
                asyncExecutor.getMaxAsyncJobsDuePerAcquisition(),
                asyncExecutor.getMaxTimerJobsPerAcquisition(),
                asyncExecutor.getResetExpiredJobsPageSize(),
                runtimeConfiguration.isAsyncExecutorResetExpiredJobsEnabled(),
                runtimeConfiguration.isAsyncExecutorUnlockOwnedJobs(),
                outboxStats.pendingCount(), outboxStats.failedCount(), outboxStats.oldestWaitingAt(),
                inboxStats.pendingCount(), inboxStats.failedCount(), inboxStats.oldestWaitingAt(),
                recentFailure.source(), recentFailure.eventId(), recentFailure.summary(), recentFailure.occurredAt(),
                jobs.count(), timers.count(), deadLetters.count());
    }

    /** Correlates workflow process rows with the latest durable event metadata. */
    public List<WorkflowRecoveryReconciliationVO> reconcile(int limit) {
        requireLimit(limit);
        if (jdbc == null) {
            throw new IllegalStateException("Recovery reconciliation requires a DataSource");
        }
        String processTenant = "CAST(p.tenant_id AS CHAR)";
        String commandTenant = normalizedTenantColumn("c.tenant_scope");
        String durableTenant = normalizedJsonTenantScope("o.payload_json");
        String taskTenant = normalizedTenantColumn("t.tenant_scope");
        String reconciliationSql = """
                SELECT p.business_id, p.business_key, p.process_instance_id, p.business_round AS round,
                       p.business_table, p.status AS business_status,
                       p.start_request_id AS request_id,
                       (SELECT c.status FROM wf_command c
                          WHERE c.request_id = p.start_request_id
                            AND c.process_instance_id = p.process_instance_id
                            AND %1$s = %2$s
                          ORDER BY c.created_at DESC, c.id DESC LIMIT 1) AS command_status,
                       (SELECT o.event_id FROM reliable_outbox o
                          WHERE o.source_owner = 'workflow'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                            AND %3$s = %2$s
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS event_id,
                       (SELECT o.type FROM reliable_outbox o
                          WHERE o.source_owner = 'workflow'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                            AND %3$s = %2$s
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS event_type,
                       (SELECT o.status FROM reliable_outbox o
                          WHERE o.source_owner = 'workflow'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                            AND %3$s = %2$s
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS durable_status,
                       (SELECT t.operation_id FROM wf_business_task t
                         WHERE t.process_instance_id = p.process_instance_id
                           AND %4$s = %2$s
                         ORDER BY t.updated_at DESC, t.operation_id DESC LIMIT 1) AS operation_id,
                       (SELECT t.status FROM wf_business_task t
                         WHERE t.process_instance_id = p.process_instance_id
                           AND %4$s = %2$s
                         ORDER BY t.updated_at DESC, t.operation_id DESC LIMIT 1) AS business_task_status,
                       (SELECT t.compensation_id FROM wf_business_task t
                         WHERE t.process_instance_id = p.process_instance_id
                           AND %4$s = %2$s
                         ORDER BY t.updated_at DESC, t.operation_id DESC LIMIT 1) AS compensation_id
                  FROM wf_process_instance p
                 WHERE p.del_flag = '0'
                """.formatted(commandTenant, processTenant, durableTenant, taskTenant);
        List<WorkflowRecoveryReconciliationVO> businessRows = jdbc.queryForList(reconciliationSql
                + tenantPredicate("p.tenant_id", false) + """
                 ORDER BY p.update_time DESC, p.id DESC
                 LIMIT ?
                """, reconciliationArgs(limit)).stream().map(WorkflowRecoveryService::toReconciliation).toList();
        return WorkflowRecoveryQuarantine.merge(OWNER, businessRows, unresolvedQuarantine(limit), limit);
    }

    public WorkflowRecoveryActionVO retryOutbox(String eventId, String reason) {
        RecoveryTenantScope.requireWritable();
        requireEventId(eventId);
        requireReason(reason);
        requireVisibleOutbox(eventId);
        return retry(eventId, reason, "OUTBOX", "OUTBOX_RETRY",
                auditCallback -> outbox.retry(OWNER, eventId, auditCallback));
    }

    public WorkflowRecoveryActionVO retryInbox(String eventId, String reason) {
        RecoveryTenantScope.requireWritable();
        requireEventId(eventId);
        requireReason(reason);
        JdbcInboxStore.Snapshot saved = inbox.find(OWNER, eventId);
        if (saved == null) throw new IllegalArgumentException("Inbox event does not exist");
        RecoveryTenantScope.requireVisible(saved.message());
        return retry(eventId, reason, "INBOX", "INBOX_RETRY",
                auditCallback -> inbox.retry(OWNER, eventId, auditCallback));
    }

    public WorkflowRecoveryReplayVO replayQuarantine(String evidenceId, String reason) {
        RecoveryTenantScope.requireWritable();
        requireReason(reason);
        JdbcQuarantineStore.Snapshot snapshot = quarantine.find(requireEvidence(evidenceId));
        if (snapshot == null) {
            throw new IllegalArgumentException("隔离证据不存在");
        }
        if (snapshot.bodyJson() == null || snapshot.bodyJson().isBlank()) {
            throw new IllegalArgumentException("隔离证据不包含可重放报文");
        }
        DurableMessage message;
        try {
            message = new DurableMessageWireCodec().decode(snapshot.bodyJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (RuntimeException ex) {
            throw ex;
        }
        RecoveryTenantScope.requireVisible(message);
        BixiUser actor = access.currentUser();
        if (executor == null) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, false, reason);
            throw new IllegalStateException("Inbox executor is not configured");
        }
        if (!OWNER.equals(message.targetOwner())) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, false, reason);
            throw new IllegalArgumentException("隔离报文目标不属于当前 owner");
        }
        try {
            DurableMessageHandler.Result result = executor.replay(message, committed -> {
                boolean accepted = committed == DurableMessageHandler.Result.PROCESSED
                        || committed == DurableMessageHandler.Result.IGNORED;
                audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId,
                        accepted, reason);
            });
            boolean accepted = result == DurableMessageHandler.Result.PROCESSED
                    || result == DurableMessageHandler.Result.IGNORED;
            return new WorkflowRecoveryReplayVO(evidenceId, message.eventId(), result.name(), accepted);
        } catch (RuntimeException failure) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, false, reason);
            throw failure;
        }
    }

    private WorkflowRecoveryActionVO retry(String eventId, String reason, String direction, String action,
            java.util.function.Function<java.util.function.Consumer<Boolean>, Boolean> operation) {
        requireEventId(eventId);
        requireReason(reason);
        BixiUser actor = access.currentUser();
        boolean changed = operation.apply(result ->
                audit.record(actor.getId(), action, OWNER, eventId, null, result, reason));
        return new WorkflowRecoveryActionVO(direction, eventId, changed);
    }

    private void requireVisibleOutbox(String eventId) {
        JdbcOutboxStore.AdminSnapshot saved = outbox.find(OWNER, eventId);
        if (saved == null) throw new IllegalArgumentException("Outbox event does not exist");
        RecoveryTenantScope.requireVisible(saved.message());
    }

    private static <T> List<T> visibleInventory(int limit,
            java.util.function.LongFunction<List<T>> pageLoader,
            java.util.function.Function<T, DurableMessage> message) {
        List<T> visible = new ArrayList<>(limit);
        long offset = 0;
        while (visible.size() < limit) {
            List<T> page = pageLoader.apply(offset);
            for (T row : page) {
                if (RecoveryTenantScope.isVisible(message.apply(row))) {
                    visible.add(row);
                    if (visible.size() == limit) break;
                }
            }
            if (page.size() < 200) break;
            offset = Math.addExact(offset, page.size());
        }
        return List.copyOf(visible);
    }

    private static DurableMessage quarantineMessage(JdbcQuarantineStore.Snapshot snapshot) {
        if (snapshot == null || snapshot.bodyJson() == null || snapshot.bodyJson().isBlank()) {
            return null;
        }
        try {
            return new DurableMessageWireCodec().decode(
                    snapshot.bodyJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (RuntimeException invalidWire) {
            return null;
        }
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireEventId(String eventId) {
        if (eventId == null || !UUID.fromString(eventId).toString().equals(eventId)) {
            throw new IllegalArgumentException("eventId must be a canonical lowercase UUID");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 256) {
            throw new IllegalArgumentException("Recovery reason must be 1 to 256 characters");
        }
    }

    private static String requireEvidence(String evidenceId) {
        if (evidenceId == null || evidenceId.isBlank() || evidenceId.length() > 128) {
            throw new IllegalArgumentException("隔离证据ID无效");
        }
        return evidenceId;
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("Limit must be between 1 and 200");
        }
    }

    private void requireJdbc() {
        if (jdbc == null) throw new IllegalStateException("Recovery inventory requires a DataSource");
    }

    private static void requireQuery(WorkflowRecoveryQueryDTO query) {
        if (query == null) throw new IllegalArgumentException("Recovery query is required");
        if (query.getCurrent() < 1 || query.getSize() < 1 || query.getSize() > 200) {
            throw new IllegalArgumentException("current must be positive and size must be between 1 and 200");
        }
        if (query.getStartTime() != null && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
        if (query.getBusinessId() != null && query.getBusinessId() < 1) {
            throw new IllegalArgumentException("businessId must be positive");
        }
    }

    private static long pageOffset(WorkflowRecoveryQueryDTO query) {
        try {
            return Math.multiplyExact(query.getCurrent() - 1L, query.getSize());
        }
        catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Recovery page offset exceeds Long range", overflow);
        }
    }

    private static void requireMatchingWinner(WorkflowRecoveryRequestDTO request, String owner,
            String resourceType, String resourceId, WorkflowRecoveryAuditStore.SavedRequest winner) {
        WorkflowRecoveryResultVO result = winner == null ? null : winner.result();
        boolean eventType = resourceType == null
                ? result != null && Set.of("OUTBOX", "INBOX").contains(result.resourceType())
                : resourceType.equals(result == null ? null : result.resourceType());
        if (winner == null || result == null || !request.reason().equals(winner.reason())
                || !request.expectedStatus().equals(winner.expectedStatus())
                || !request.requestId().equals(result.requestId()) || !owner.equals(result.owner())
                || !resourceId.equals(result.resourceId()) || !eventType
                || !request.expectedStatus().equals(result.previousStatus())) {
            throw new IllegalStateException("requestId 已用于不同的恢复请求");
        }
    }

    private static String requireOwner(String owner) {
        if (owner != null && !owner.isBlank() && !OWNER.equals(owner)) {
            throw new IllegalArgumentException("Recovery owner must be workflow");
        }
        return OWNER;
    }

    private static void requireRequest(WorkflowRecoveryRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("Recovery request is required");
        requireEventId(request.requestId());
        requireReason(request.reason());
        if (request.expectedStatus() == null
                || !request.expectedStatus().matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("expectedStatus is invalid");
        }
    }

    private static String eventUnion() {
        String tenantScope = textualJsonTenantScope("payload_json");
        return """
                SELECT 'OUTBOX' AS direction, source_owner AS event_owner, target_owner AS peer_owner,
                       event_id, type AS event_type, schema_version, payload_hash, status AS event_status,
                       %1$s AS tenant_scope,
                       attempts, next_attempt_at, lease_until, last_error, created_at,
                       delivered_at AS completed_at,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessId')) AS business_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessKey')) AS business_key,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.processInstanceId')) AS process_instance_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.commandId')) AS request_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.operationId')) AS operation_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.compensationId')) AS compensation_id
                  FROM reliable_outbox
                UNION ALL
                SELECT 'INBOX' AS direction, target_owner AS event_owner, source_owner AS peer_owner,
                       event_id, type AS event_type, schema_version, payload_hash, status AS event_status,
                       %1$s AS tenant_scope,
                       attempts, next_attempt_at, lease_until, last_error, received_at AS created_at,
                       processed_at AS completed_at,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessId')) AS business_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessKey')) AS business_key,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.processInstanceId')) AS process_instance_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.commandId')) AS request_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.operationId')) AS operation_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.compensationId')) AS compensation_id
                  FROM reliable_inbox
                """.formatted(tenantScope);
    }

    private static List<Object> eventArgs(String owner, WorkflowRecoveryQueryDTO query) {
        List<Object> args = new ArrayList<>();
        args.add(owner);
        addTenantArg(args);
        twice(args, query.getDirection());
        twice(args, query.getType());
        twice(args, query.getStatus());
        twice(args, timestamp(query.getStartTime()));
        twice(args, timestamp(query.getEndTime()));
        twice(args, query.getBusinessId() == null ? null : query.getBusinessId().toString());
        return args;
    }

    private static List<Object> commonArgs(WorkflowRecoveryQueryDTO query) {
        List<Object> args = new ArrayList<>();
        addTenantArg(args);
        twice(args, query.getType());
        twice(args, query.getStatus());
        twice(args, timestamp(query.getStartTime()));
        twice(args, timestamp(query.getEndTime()));
        twice(args, query.getBusinessId());
        return args;
    }

    private static List<Object> taskArgs(WorkflowRecoveryQueryDTO query) {
        List<Object> args = new ArrayList<>();
        addTenantArg(args);
        twice(args, query.getStatus());
        twice(args, timestamp(query.getStartTime()));
        twice(args, timestamp(query.getEndTime()));
        twice(args, query.getBusinessId());
        return args;
    }

    private static Object[] reconciliationArgs(int limit) {
        List<Object> args = new ArrayList<>();
        addTenantArg(args);
        args.add(limit);
        return args.toArray();
    }

    private static String tenantPredicate(String column, boolean defaultAlias) {
        if (TenantContextHolder.isAllTenantsReadOnly()) {
            return "";
        }
        if (defaultAlias && RecoveryTenantScope.currentTenantId() == 1L) {
            return " AND (" + column + " = ? OR " + column + " = 'default')";
        }
        return " AND " + column + " = ?";
    }

    private static String textualJsonTenantScope(String payloadColumn) {
        return "CASE WHEN JSON_TYPE(JSON_EXTRACT(" + payloadColumn + ", '$.tenantScope')) = 'STRING'"
                + " THEN JSON_UNQUOTE(JSON_EXTRACT(" + payloadColumn + ", '$.tenantScope')) END";
    }

    private static String normalizedJsonTenantScope(String payloadColumn) {
        String scope = textualJsonTenantScope(payloadColumn);
        return "CASE WHEN " + scope + " = 'default' THEN '1' ELSE " + scope + " END";
    }

    private static String normalizedTenantColumn(String column) {
        return "CASE WHEN " + column + " = 'default' THEN '1' ELSE " + column + " END";
    }

    private static void addTenantArg(List<Object> args) {
        if (!TenantContextHolder.isAllTenantsReadOnly()) {
            args.add(RecoveryTenantScope.currentScope());
        }
    }

    private static void twice(List<Object> args, Object value) {
        args.add(value);
        args.add(value);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static WorkflowRecoveryEventVO toEvent(Map<String, Object> row) {
        return new WorkflowRecoveryEventVO(text(row.get("direction")), text(row.get("event_owner")),
                text(row.get("event_id")), text(row.get("peer_owner")), text(row.get("event_type")),
                integer(row.get("schema_version")) == null ? 0 : integer(row.get("schema_version")),
                text(row.get("payload_hash")), text(row.get("event_status")),
                integer(row.get("attempts")) == null ? 0 : integer(row.get("attempts")),
                number(row.get("business_id")), text(row.get("business_key")),
                text(row.get("process_instance_id")), text(row.get("request_id")),
                text(row.get("operation_id")), text(row.get("compensation_id")),
                instant(row.get("next_attempt_at")), instant(row.get("lease_until")),
                truncate(text(row.get("last_error"))), instant(row.get("created_at")),
                instant(row.get("completed_at")));
    }

    private static WorkflowRecoveryCommandVO toCommand(Map<String, Object> row) {
        return new WorkflowRecoveryCommandVO(OWNER, text(row.get("command_id")), text(row.get("request_id")),
                text(row.get("operation")), text(row.get("resource_id")), text(row.get("status")),
                text(row.get("process_instance_id")), number(row.get("business_id")),
                text(row.get("business_key")), number(row.get("actor_id")), truncate(text(row.get("error"))),
                instant(row.get("created_at")), instant(row.get("completed_at")));
    }

    private static WorkflowRecoveryBusinessTaskVO toBusinessTask(Map<String, Object> row) {
        return new WorkflowRecoveryBusinessTaskVO(OWNER, text(row.get("operation_id")),
                text(row.get("process_instance_id")), text(row.get("business_table")),
                number(row.get("business_id")), integer(row.get("round")), text(row.get("status")),
                text(row.get("result_event_id")), text(row.get("compensation_id")),
                instant(row.get("deadline")), truncate(text(row.get("last_error"))),
                instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private static boolean isBusinessTaskTerminal(String status) {
        return Set.of("SUCCEEDED", "COMPENSATED", "CANCELED").contains(status);
    }

    private static WorkflowRecoveryReconciliationVO toReconciliation(java.util.Map<String, Object> row) {
        String durable = text(row.get("durable_status"));
        String business = text(row.get("business_status"));
        String classification = UpmsRecoveryClassification.classify(durable, business);
        return new WorkflowRecoveryReconciliationVO(OWNER, text(row.get("event_id")), text(row.get("event_type")),
                text(row.get("business_table")), number(row.get("business_id")), text(row.get("business_key")),
                text(row.get("process_instance_id")), integer(row.get("round")), text(row.get("request_id")),
                text(row.get("command_status")), text(row.get("operation_id")),
                text(row.get("business_task_status")), text(row.get("compensation_id")), null, durable, business,
                classification, detail(classification));
    }

    private List<WorkflowRecoveryQuarantine.Evidence> unresolvedQuarantine(int limit) {
        List<WorkflowRecoveryQuarantine.Evidence> evidence = new ArrayList<>();
        long offset = 0;
        while (evidence.size() < limit) {
            List<JdbcQuarantineStore.Snapshot> page = quarantine.listPage(200, offset);
            for (JdbcQuarantineStore.Snapshot snapshot : page) {
                DurableMessage message = quarantineMessage(snapshot);
                if (message == null || !RecoveryTenantScope.isVisible(message)) {
                    continue;
                }
                if (!OWNER.equals(message.targetOwner()) || resolved(message.eventId())) {
                    continue;
                }
                WorkflowRecoveryMetadata metadata = WorkflowRecoveryMetadata.parse(message.payloadJson()).orElse(null);
                evidence.add(new WorkflowRecoveryQuarantine.Evidence(
                        message.eventId(), message.type(), snapshot.reason(), metadata));
                if (evidence.size() == limit) break;
            }
            if (evidence.size() == limit || page.size() < 200) break;
            offset = Math.addExact(offset, page.size());
        }
        return List.copyOf(evidence);
    }

    private boolean resolved(String eventId) {
        JdbcInboxStore.Snapshot received = inbox.find(OWNER, eventId);
        return received != null && (received.state() == JdbcInboxStore.State.PROCESSED
                || received.state() == JdbcInboxStore.State.IGNORED);
    }

    private static String detail(String classification) {
        return switch (classification) {
            case "PENDING_DELIVERY" -> "流程记录已创建，但尚未找到可交付的 Workflow Outbox 记录";
            case "FAILED_DELIVERY" -> "最近一条 Workflow Outbox 投递失败，需要重试或人工核查";
            case "MATCHED" -> "流程状态与 durable 投递记录均存在";
            default -> "流程记录与 durable 记录无法完成关联";
        };
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static Long number(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof java.math.BigDecimal decimal) return decimal.longValueExact();
            return Long.parseLong(value.toString());
        }
        catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("业务 ID 超出 Long 精确范围", invalid);
        }
    }
    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }

    private DurableStats durableStats(String table, String ownerColumn, String waitingColumn,
            String pendingStatuses, String owner) {
        String tenant = tenantPredicate(textualJsonTenantScope("payload_json"), true);
        List<Object> args = new ArrayList<>(List.of(owner));
        addTenantArg(args);
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN status IN (%s) THEN 1 ELSE 0 END), 0)
                           AS pending_count,
                       COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_count,
                       MIN(CASE WHEN status IN (%s) THEN %s END) AS oldest_waiting_at
                  FROM %s
                 WHERE %s = ?
                """.formatted(pendingStatuses, pendingStatuses, waitingColumn, table, ownerColumn)
                + tenant, args.toArray());
        return new DurableStats(longValue(row.get("pending_count")), longValue(row.get("failed_count")),
                instant(row.get("oldest_waiting_at")));
    }

    private RecentFailure recentFailure() {
        String tenant = tenantPredicate(textualJsonTenantScope("payload_json"), true);
        List<Object> args = new ArrayList<>(List.of(OWNER));
        addTenantArg(args);
        args.add(OWNER);
        addTenantArg(args);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT source, event_id, last_error, occurred_at
                  FROM (
                        SELECT 'OUTBOX' AS source, event_id, last_error, created_at AS occurred_at
                          FROM reliable_outbox
                         WHERE source_owner = ? AND status = 'FAILED'
                """ + tenant + """
                        UNION ALL
                        SELECT 'INBOX' AS source, event_id, last_error, received_at AS occurred_at
                          FROM reliable_inbox
                         WHERE target_owner = ? AND status = 'FAILED'
                """ + tenant + """
                       ) failures
                 ORDER BY occurred_at DESC
                 LIMIT 1
                """, args.toArray());
        if (rows.isEmpty()) {
            return RecentFailure.none();
        }
        Map<String, Object> row = rows.get(0);
        return new RecentFailure(text(row.get("source")), text(row.get("event_id")),
                truncate(text(row.get("last_error"))), instant(row.get("occurred_at")));
    }

    private static long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime localDateTime) return localDateTime.toInstant(ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        return Instant.parse(value.toString());
    }

    private static String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 256));
    }

    private record DurableStats(long pendingCount, long failedCount, Instant oldestWaitingAt) { }

    private record RecentFailure(String source, String eventId, String summary, Instant occurredAt) {
        private static RecentFailure none() { return new RecentFailure(null, null, null, null); }
    }

    /** Shared classification semantics are intentionally kept local to the workflow module. */
    private static final class UpmsRecoveryClassification {
        static String classify(String durableStatus, String businessStatus) {
            if ("FAILED".equals(durableStatus)) return "FAILED_DELIVERY";
            if (durableStatus == null && "running".equals(businessStatus)) return "PENDING_DELIVERY";
            if (durableStatus != null && businessStatus != null) return "MATCHED";
            return "BUSINESS_MISMATCH";
        }
    }
}
