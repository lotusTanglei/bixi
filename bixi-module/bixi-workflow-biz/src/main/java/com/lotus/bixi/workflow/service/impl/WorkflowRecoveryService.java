package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.event.WorkflowRecoveryMetadata;
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
        return outbox.list(OWNER, normalizeStatus(status), limit).stream().map(snapshot ->
                new WorkflowOutboxSnapshotVO(snapshot.message().sourceOwner(), snapshot.message().eventId(),
                        snapshot.message().targetOwner(), snapshot.message().type(), snapshot.message().schemaVersion(),
                        snapshot.message().payloadHash(), snapshot.status(), snapshot.attempts(),
                        snapshot.nextAttemptAt(), snapshot.leaseUntil(), snapshot.lastError(),
                        snapshot.createdAt(), snapshot.deliveredAt())).toList();
    }

    public List<WorkflowInboxSnapshotVO> listInbox(String status, int limit) {
        return inbox.list(OWNER, normalizeStatus(status), limit).stream().map(snapshot ->
                new WorkflowInboxSnapshotVO(snapshot.message().targetOwner(), snapshot.message().eventId(),
                        snapshot.message().sourceOwner(), snapshot.message().type(), snapshot.message().schemaVersion(),
                        snapshot.message().payloadHash(), snapshot.status(), snapshot.attempts(),
                        snapshot.nextAttemptAt(), snapshot.leaseUntil(), snapshot.lastError(),
                        snapshot.receivedAt(), snapshot.processedAt())).toList();
    }

    public List<WorkflowQuarantineSnapshotVO> listQuarantine(int limit) {
        return quarantine.list(limit).stream().map(snapshot -> new WorkflowQuarantineSnapshotVO(
                snapshot.evidenceId(), snapshot.bodyJson(), snapshot.reason(), snapshot.quarantinedAt())).toList();
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
                management.createJobQuery().count(),
                management.createTimerJobQuery().count(),
                management.createDeadLetterJobQuery().count());
    }

    /** Correlates workflow process rows with the latest durable event metadata. */
    public List<WorkflowRecoveryReconciliationVO> reconcile(int limit) {
        requireLimit(limit);
        if (jdbc == null) {
            throw new IllegalStateException("Recovery reconciliation requires a DataSource");
        }
        List<WorkflowRecoveryReconciliationVO> businessRows = jdbc.queryForList("""
                SELECT p.business_id, p.business_key, p.process_instance_id, p.business_round AS round,
                       p.business_table, p.status AS business_status,
                       p.start_request_id AS request_id,
                       (SELECT c.status FROM wf_command c
                          WHERE c.request_id = p.start_request_id
                            AND c.process_instance_id = p.process_instance_id
                          ORDER BY c.created_at DESC LIMIT 1) AS command_status,
                       (SELECT o.event_id FROM reliable_outbox o
                          WHERE o.source_owner = 'workflow'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                          ORDER BY o.created_at DESC LIMIT 1) AS event_id,
                       (SELECT o.type FROM reliable_outbox o
                          WHERE o.source_owner = 'workflow'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                          ORDER BY o.created_at DESC LIMIT 1) AS event_type,
                       (SELECT o.status FROM reliable_outbox o
                          WHERE o.source_owner = 'workflow'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                          ORDER BY o.created_at DESC LIMIT 1) AS durable_status,
                       (SELECT JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.payload.operationId'))
                          FROM reliable_outbox o
                         WHERE o.source_owner = 'workflow'
                           AND o.type = 'WORKFLOW_BUSINESS_TASK_REQUESTED'
                           AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                         ORDER BY o.created_at DESC LIMIT 1) AS operation_id,
                       (SELECT o.status FROM reliable_outbox o
                         WHERE o.source_owner = 'workflow'
                           AND o.type = 'WORKFLOW_BUSINESS_TASK_REQUESTED'
                           AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                         ORDER BY o.created_at DESC LIMIT 1) AS business_task_status,
                       (SELECT JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.payload.compensationId'))
                          FROM reliable_outbox o
                         WHERE o.source_owner = 'workflow'
                           AND o.type = 'WORKFLOW_COMPENSATION_REQUESTED'
                           AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.processInstanceId')) = p.process_instance_id
                         ORDER BY o.created_at DESC LIMIT 1) AS compensation_id
                  FROM wf_process_instance p
                 WHERE p.del_flag = '0'
                 ORDER BY p.update_time DESC, p.id DESC
                 LIMIT ?
                """, limit).stream().map(WorkflowRecoveryService::toReconciliation).toList();
        return WorkflowRecoveryQuarantine.merge(OWNER, businessRows, unresolvedQuarantine(limit), limit);
    }

    public WorkflowRecoveryActionVO retryOutbox(String eventId, String reason) {
        return retry(eventId, reason, "OUTBOX", "OUTBOX_RETRY", () -> outbox.retry(OWNER, eventId));
    }

    public WorkflowRecoveryActionVO retryInbox(String eventId, String reason) {
        return retry(eventId, reason, "INBOX", "INBOX_RETRY", () -> inbox.retry(OWNER, eventId));
    }

    public WorkflowRecoveryReplayVO replayQuarantine(String evidenceId, String reason) {
        requireReason(reason);
        BixiUser actor = access.currentUser();
        JdbcQuarantineStore.Snapshot snapshot = quarantine.find(requireEvidence(evidenceId));
        if (snapshot == null) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, null, evidenceId, false, reason);
            throw new IllegalArgumentException("隔离证据不存在");
        }
        if (snapshot.bodyJson() == null || snapshot.bodyJson().isBlank()) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, null, evidenceId, false, reason);
            throw new IllegalArgumentException("隔离证据不包含可重放报文");
        }
        DurableMessage message;
        try {
            message = new DurableMessageWireCodec().decode(snapshot.bodyJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (RuntimeException ex) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, null, evidenceId, false, reason);
            throw ex;
        }
        if (executor == null) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, false, reason);
            throw new IllegalStateException("Inbox executor is not configured");
        }
        if (!OWNER.equals(message.targetOwner())) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, false, reason);
            throw new IllegalArgumentException("隔离报文目标不属于当前 owner");
        }
        try {
            DurableMessageHandler.Result result = executor.receive(message);
            boolean accepted = result == DurableMessageHandler.Result.PROCESSED
                    || result == DurableMessageHandler.Result.IGNORED;
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, accepted, reason);
            return new WorkflowRecoveryReplayVO(evidenceId, message.eventId(), result.name(), accepted);
        } catch (RuntimeException failure) {
            audit.record(actor.getId(), "QUARANTINE_REPLAY", OWNER, message.eventId(), evidenceId, false, reason);
            throw failure;
        }
    }

    private WorkflowRecoveryActionVO retry(String eventId, String reason, String direction, String action,
            java.util.function.BooleanSupplier operation) {
        requireEventId(eventId);
        requireReason(reason);
        BixiUser actor = access.currentUser();
        boolean changed = operation.getAsBoolean();
        audit.record(actor.getId(), action, OWNER, eventId, null, changed, reason);
        return new WorkflowRecoveryActionVO(direction, eventId, changed);
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
        DurableMessageWireCodec codec = new DurableMessageWireCodec();
        for (JdbcQuarantineStore.Snapshot snapshot : quarantine.list(limit)) {
            DurableMessage message;
            try {
                message = codec.decode(snapshot.bodyJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            catch (RuntimeException invalidWire) {
                continue;
            }
            if (!OWNER.equals(message.targetOwner()) || resolved(message.eventId())) {
                continue;
            }
            WorkflowRecoveryMetadata metadata = WorkflowRecoveryMetadata.parse(message.payloadJson()).orElse(null);
            evidence.add(new WorkflowRecoveryQuarantine.Evidence(
                    message.eventId(), message.type(), snapshot.reason(), metadata));
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
    private static Long number(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }

    private DurableStats durableStats(String table, String ownerColumn, String waitingColumn,
            String pendingStatuses, String owner) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT COALESCE(SUM(CASE WHEN status IN (%s) THEN 1 ELSE 0 END), 0)
                           AS pending_count,
                       COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed_count,
                       MIN(CASE WHEN status IN (%s) THEN %s END) AS oldest_waiting_at
                  FROM %s
                 WHERE %s = ?
                """.formatted(pendingStatuses, pendingStatuses, waitingColumn, table, ownerColumn), owner);
        return new DurableStats(longValue(row.get("pending_count")), longValue(row.get("failed_count")),
                instant(row.get("oldest_waiting_at")));
    }

    private RecentFailure recentFailure() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT source, event_id, last_error, occurred_at
                  FROM (
                        SELECT 'OUTBOX' AS source, event_id, last_error, created_at AS occurred_at
                          FROM reliable_outbox
                         WHERE source_owner = ? AND status = 'FAILED'
                        UNION ALL
                        SELECT 'INBOX' AS source, event_id, last_error, received_at AS occurred_at
                          FROM reliable_inbox
                         WHERE target_owner = ? AND status = 'FAILED'
                       ) failures
                 ORDER BY occurred_at DESC
                 LIMIT 1
                """, OWNER, OWNER);
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
