package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.RecoveryTenantScope;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.event.WorkflowRecoveryMetadata;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO;
import com.lotus.bixi.workflow.api.dto.WorkflowRecoveryRequestDTO;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;

/** Operator-facing recovery operations for the UPMS owner. */
@Service
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
public class UpmsRecoveryService {
    public static final String OWNER = "upms";

    private final JdbcOutboxStore outbox;
    private final JdbcInboxStore inbox;
    private final JdbcQuarantineStore quarantine;
    private final UpmsRecoveryAuditStore audit;
    private final UpmsRecoveryAccessService access;
    private final JdbcTemplate jdbc;
    private final InboxExecutor executor;

    public UpmsRecoveryService(@Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("upmsInboxStore") JdbcInboxStore inbox,
            @Qualifier("upmsQuarantineStore") JdbcQuarantineStore quarantine,
            UpmsRecoveryAuditStore audit, UpmsRecoveryAccessService access) {
        this(outbox, inbox, quarantine, audit, access, (JdbcTemplate) null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UpmsRecoveryService(@Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("upmsInboxStore") JdbcInboxStore inbox,
            @Qualifier("upmsQuarantineStore") JdbcQuarantineStore quarantine,
            UpmsRecoveryAuditStore audit, UpmsRecoveryAccessService access, javax.sql.DataSource dataSource,
            @Qualifier("upmsInboxExecutor") InboxExecutor executor) {
        this(outbox, inbox, quarantine, audit, access,
                dataSource == null ? null : new JdbcTemplate(dataSource), executor);
    }

    UpmsRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            UpmsRecoveryAuditStore audit, UpmsRecoveryAccessService access, JdbcTemplate jdbc) {
        this(outbox, inbox, quarantine, audit, access, jdbc, null);
    }

    public UpmsRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            UpmsRecoveryAuditStore audit, UpmsRecoveryAccessService access, InboxExecutor executor) {
        this(outbox, inbox, quarantine, audit, access, (JdbcTemplate) null, executor);
    }

    private UpmsRecoveryService(JdbcOutboxStore outbox, JdbcInboxStore inbox, JdbcQuarantineStore quarantine,
            UpmsRecoveryAuditStore audit, UpmsRecoveryAccessService access, JdbcTemplate jdbc,
            InboxExecutor executor) {
        this.outbox = outbox;
        this.inbox = inbox;
        this.quarantine = quarantine;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
        this.executor = executor;
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
                        UpmsRecoveryService::quarantinedMessage);
        return snapshots.stream().map(snapshot -> new WorkflowQuarantineSnapshotVO(
                snapshot.evidenceId(), null, snapshot.reason(), snapshot.quarantinedAt())).toList();
    }

    public WorkflowRecoveryPageVO<WorkflowRecoveryEventVO> pageLeaveEvents(WorkflowRecoveryQueryDTO query) {
        requireJdbc();
        requireQuery(query);
        long offset = pageOffset(query);
        requireOwner(query.getOwner());
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        BixiUser actor = allTenants ? null : currentActor();
        String where = leaveEventWhere(allTenants);
        List<Object> args = leaveEventArgs(actor, query, allTenants);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + eventUnion() + ") e" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        List<WorkflowRecoveryEventVO> rows = jdbc.queryForList("SELECT * FROM (" + eventUnion() + ") e"
                + where + " ORDER BY created_at DESC, event_id DESC LIMIT ? OFFSET ?", pageArgs.toArray())
                .stream().map(UpmsRecoveryService::toEvent).toList();
        return WorkflowRecoveryPageVO.of(rows, total == null ? 0 : total,
                query.getCurrent(), query.getSize());
    }

    public WorkflowRecoveryEventVO leaveEvent(String eventId) {
        requireJdbc();
        requireEventId(eventId);
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        BixiUser actor = allTenants ? null : currentActor();
        String visibility = allTenants
                ? " AND EXISTS (SELECT 1 FROM demo_leave_request l WHERE l.id = e.business_id AND l.del_flag = '0')"
                : " AND CASE WHEN e.tenant_scope = 'default' THEN '1' ELSE e.tenant_scope END = ?"
                        + " AND EXISTS (SELECT 1 FROM demo_leave_request l WHERE l.id = e.business_id"
                        + " AND l.applicant_id = ? AND l.tenant_id = ? AND l.del_flag = '0')";
        List<Object> args = new ArrayList<>(List.of(OWNER, eventId));
        if (!allTenants) {
            args.add(RecoveryTenantScope.currentScope());
            args.add(actor.getId());
            args.add(RecoveryTenantScope.currentTenantId());
        }
        List<WorkflowRecoveryEventVO> rows = jdbc.queryForList("SELECT * FROM (" + eventUnion() + ") e"
                + " WHERE event_owner = ? AND event_id = ?" + visibility
                + " ORDER BY created_at DESC LIMIT 1", args.toArray())
                .stream().map(UpmsRecoveryService::toEvent).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("请假恢复事件不存在或不可见");
        return rows.get(0);
    }

    public WorkflowRecoveryPageVO<WorkflowRecoveryCommandVO> pageLeaveCommands(WorkflowRecoveryQueryDTO query) {
        requireJdbc();
        requireQuery(query);
        long offset = pageOffset(query);
        requireOwner(query.getOwner());
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        BixiUser actor = allTenants ? null : currentActor();
        String where = (allTenants ? " WHERE 1 = 1" : " WHERE c.actor_id = ? AND c.tenant_scope = ?")
                + " AND (? IS NULL OR c.operation = ?) AND (? IS NULL OR c.status = ?)"
                + " AND (? IS NULL OR c.created_at >= ?) AND (? IS NULL OR c.created_at <= ?)"
                + " AND (? IS NULL OR c.leave_id = ?)";
        List<Object> args = commandArgs(actor, query, allTenants);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_command c"
                        + " JOIN demo_leave_request l ON l.id = c.leave_id AND l.applicant_id = c.actor_id"
                        + " AND CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END"
                        + " = CAST(l.tenant_id AS CHAR)" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        List<WorkflowRecoveryCommandVO> rows = jdbc.queryForList("""
                SELECT c.command_id, c.client_request_id AS request_id, c.operation,
                       CAST(c.leave_id AS CHAR) AS resource_id, c.status, c.process_instance_id,
                       c.leave_id AS business_id, l.business_key, c.actor_id, c.error_code AS error,
                       c.created_at, c.completed_at
                  FROM demo_leave_command c
                  JOIN demo_leave_request l ON l.id = c.leave_id AND l.applicant_id = c.actor_id
                   AND CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END
                       = CAST(l.tenant_id AS CHAR)
                """ + where + " ORDER BY c.created_at DESC, c.command_id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray()).stream().map(UpmsRecoveryService::toCommand).toList();
        return WorkflowRecoveryPageVO.of(rows, total == null ? 0 : total,
                query.getCurrent(), query.getSize());
    }

    public WorkflowRecoveryCommandVO leaveCommand(String commandId) {
        requireJdbc();
        requireEventId(commandId);
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        BixiUser actor = allTenants ? null : currentActor();
        String visibility = allTenants ? "" : " AND c.actor_id = ? AND c.tenant_scope = ?";
        List<Object> args = new ArrayList<>(List.of(commandId));
        if (!allTenants) {
            args.add(actor.getId());
            args.add(RecoveryTenantScope.currentScope());
        }
        List<WorkflowRecoveryCommandVO> rows = jdbc.queryForList("""
                SELECT c.command_id, c.client_request_id AS request_id, c.operation,
                       CAST(c.leave_id AS CHAR) AS resource_id, c.status, c.process_instance_id,
                       c.leave_id AS business_id, l.business_key, c.actor_id, c.error_code AS error,
                       c.created_at, c.completed_at
                  FROM demo_leave_command c
                  JOIN demo_leave_request l ON l.id = c.leave_id AND l.applicant_id = c.actor_id
                   AND CASE WHEN c.tenant_scope = 'default' THEN '1' ELSE c.tenant_scope END
                       = CAST(l.tenant_id AS CHAR)
                 WHERE c.command_id = ?
                """ + visibility, args.toArray())
                .stream().map(UpmsRecoveryService::toCommand).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("请假命令不存在或不可见");
        return rows.get(0);
    }

    public WorkflowRecoveryPageVO<WorkflowRecoveryBusinessTaskVO> pageLeaveBusinessTasks(
            WorkflowRecoveryQueryDTO query) {
        requireJdbc();
        requireQuery(query);
        long offset = pageOffset(query);
        requireOwner(query.getOwner());
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        BixiUser actor = allTenants ? null : currentActor();
        String where = (allTenants ? " WHERE l.del_flag = '0'"
                : " WHERE l.applicant_id = ? AND l.tenant_id = ? AND l.del_flag = '0'")
                + " AND (? IS NULL OR b.booking_state = ?) AND (? IS NULL OR b.created_at >= ?)"
                + " AND (? IS NULL OR b.created_at <= ?) AND (? IS NULL OR b.leave_id = ?)";
        List<Object> args = bookingArgs(actor, query, allTenants);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking b"
                + " JOIN demo_leave_request l ON l.id = b.leave_id AND b.tenant_id = l.tenant_id"
                + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.getSize());
        pageArgs.add(offset);
        List<WorkflowRecoveryBusinessTaskVO> rows = jdbc.queryForList("""
                SELECT b.operation_id, l.process_instance_id, b.leave_id AS business_id,
                       b.round, b.booking_state AS status, b.compensation_id,
                       b.created_at, b.updated_at
                  FROM demo_leave_booking b
                  JOIN demo_leave_request l ON l.id = b.leave_id AND b.tenant_id = l.tenant_id
                """ + where + " ORDER BY b.created_at DESC, b.operation_id DESC LIMIT ? OFFSET ?",
                pageArgs.toArray()).stream().map(UpmsRecoveryService::toBusinessTask).toList();
        return WorkflowRecoveryPageVO.of(rows, total == null ? 0 : total,
                query.getCurrent(), query.getSize());
    }

    public WorkflowRecoveryBusinessTaskVO leaveBusinessTask(String operationId) {
        requireJdbc();
        requireEventId(operationId);
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        BixiUser actor = allTenants ? null : currentActor();
        String visibility = allTenants ? "" : " AND l.applicant_id = ? AND l.tenant_id = ?";
        List<Object> args = new ArrayList<>(List.of(operationId));
        if (!allTenants) {
            args.add(actor.getId());
            args.add(RecoveryTenantScope.currentTenantId());
        }
        List<WorkflowRecoveryBusinessTaskVO> rows = jdbc.queryForList("""
                SELECT b.operation_id, l.process_instance_id, b.leave_id AS business_id,
                       b.round, b.booking_state AS status, b.compensation_id,
                       b.created_at, b.updated_at
                  FROM demo_leave_booking b
                  JOIN demo_leave_request l ON l.id = b.leave_id AND b.tenant_id = l.tenant_id
                 WHERE b.operation_id = ? AND l.del_flag = '0'
                """ + visibility, args.toArray())
                .stream().map(UpmsRecoveryService::toBusinessTask).toList();
        if (rows.isEmpty()) throw new IllegalArgumentException("请假登记任务不存在或不可见");
        return rows.get(0);
    }

    /** Correlates UPMS leave rows with the latest durable command metadata without exposing payloads. */
    public List<WorkflowRecoveryReconciliationVO> reconcile(int limit) {
        requireLimit(limit);
        if (jdbc == null) {
            throw new IllegalStateException("Recovery reconciliation requires a DataSource");
        }
        boolean allTenants = TenantContextHolder.isAllTenantsReadOnly();
        String tenantWhere = allTenants ? "" : " AND l.tenant_id = ?";
        List<Object> args = new ArrayList<>();
        if (!allTenants) args.add(RecoveryTenantScope.currentTenantId());
        args.add(limit);
        String durableTenant = normalizedJsonTenantScope("o.payload_json");
        String reconciliationSql = """
                SELECT l.id AS business_id, l.business_key, l.process_instance_id, l.round,
                       l.leave_status AS business_status, l.start_command_id AS request_id,
                       (SELECT o.status FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND o.type = 'WORKFLOW_START_REQUESTED'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                            AND %1$s = CAST(l.tenant_id AS CHAR)
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS command_status,
                        (SELECT o.event_id FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                            AND %1$s = CAST(l.tenant_id AS CHAR)
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS event_id,
                       (SELECT o.type FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                            AND %1$s = CAST(l.tenant_id AS CHAR)
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS event_type,
                       (SELECT o.status FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                            AND %1$s = CAST(l.tenant_id AS CHAR)
                          ORDER BY o.created_at DESC, o.event_id DESC LIMIT 1) AS durable_status,
                       b.operation_id, b.booking_state AS business_task_status, b.compensation_id
                  FROM demo_leave_request l
                  LEFT JOIN demo_leave_booking b ON b.leave_id = l.id AND b.tenant_id = l.tenant_id
                   AND b.round = l.round
                 WHERE l.del_flag = '0'
                """.formatted(durableTenant);
        List<WorkflowRecoveryReconciliationVO> businessRows = jdbc.queryForList(reconciliationSql + tenantWhere + """
                 ORDER BY l.update_time DESC, l.id DESC
                 LIMIT ?
                """, args.toArray()).stream().map(UpmsRecoveryService::toReconciliation).toList();
        return WorkflowRecoveryQuarantine.merge(OWNER, businessRows, unresolvedQuarantine(limit), limit);
    }

    public WorkflowRecoveryActionVO retryOutbox(String eventId, String reason) {
        RecoveryTenantScope.requireWritable();
        return retry(eventId, reason, "OUTBOX", "OUTBOX_RETRY",
                () -> {
                    JdbcOutboxStore.AdminSnapshot snapshot = outbox.find(OWNER, eventId);
                    return snapshot == null ? null : snapshot.message();
                },
                auditCallback -> outbox.retry(OWNER, eventId, auditCallback));
    }

    public WorkflowRecoveryActionVO retryInbox(String eventId, String reason) {
        RecoveryTenantScope.requireWritable();
        return retry(eventId, reason, "INBOX", "INBOX_RETRY",
                () -> {
                    JdbcInboxStore.Snapshot snapshot = inbox.find(OWNER, eventId);
                    return snapshot == null ? null : snapshot.message();
                },
                auditCallback -> inbox.retry(OWNER, eventId, auditCallback));
    }

    public WorkflowRecoveryResultVO retryLeaveEvent(String eventId, WorkflowRecoveryRequestDTO request) {
        RecoveryTenantScope.requireWritable();
        requireRequest(request);
        WorkflowRecoveryEventVO saved = leaveEvent(eventId);
        UpmsRecoveryAuditStore.SavedRequest replay = audit.findRequest(OWNER, "LEAVE_EVENT_RETRY",
                request.requestId());
        if (replay != null) {
            requireMatchingWinner(request, OWNER, null, eventId, replay);
            return replay.result();
        }
        if (!request.expectedStatus().equals(saved.status())) {
            throw new IllegalStateException("请假事件状态与 expectedStatus 不一致");
        }
        if ("IN_FLIGHT".equals(saved.status()) && saved.leaseUntil() != null
                && saved.leaseUntil().isAfter(Instant.now())) {
            throw new IllegalStateException("请假事件仍由活跃租约持有，不能抢占");
        }
        if (!"FAILED".equals(saved.status())) {
            throw new IllegalStateException("仅 FAILED 事件可以人工重试");
        }
        BixiUser actor = currentActor();
        java.util.concurrent.atomic.AtomicReference<WorkflowRecoveryResultVO> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.function.Consumer<Boolean> callback = changed -> {
            String current = changed ? ("OUTBOX".equals(saved.direction()) ? "PENDING" : "RECEIVED") : saved.status();
            WorkflowRecoveryResultVO value = new WorkflowRecoveryResultVO(request.requestId(), OWNER,
                    saved.direction(), eventId, saved.status(), current, changed,
                    changed ? "RETRIED" : "UNCHANGED",
                    changed ? "请假事件已重新进入 owner 队列" : "事件状态已变化，未执行重试", Instant.now());
            audit.recordRequest(actor.getId(), "LEAVE_EVENT_RETRY", OWNER, eventId, request.reason(),
                    request.expectedStatus(), value);
            result.set(value);
        };
        try {
            if ("OUTBOX".equals(saved.direction())) outbox.retry(OWNER, eventId, callback);
            else inbox.retry(OWNER, eventId, callback);
        }
        catch (org.springframework.dao.DuplicateKeyException race) {
            UpmsRecoveryAuditStore.SavedRequest winner = audit.findRequest(OWNER, "LEAVE_EVENT_RETRY",
                    request.requestId());
            if (winner != null) {
                requireMatchingWinner(request, OWNER, saved.direction(), eventId, winner);
                return winner.result();
            }
            throw race;
        }
        return result.get();
    }

    public WorkflowRecoveryResultVO retryLeaveCommand(String commandId, WorkflowRecoveryRequestDTO request) {
        RecoveryTenantScope.requireWritable();
        requireRequest(request);
        WorkflowRecoveryCommandVO command = leaveCommand(commandId);
        UpmsRecoveryAuditStore.SavedRequest replay = audit.findRequest(OWNER, "LEAVE_COMMAND_RETRY",
                request.requestId());
        if (replay != null) {
            requireMatchingWinner(request, OWNER, "COMMAND", commandId, replay);
            return replay.result();
        }
        if (!request.expectedStatus().equals(command.status())) {
            throw new IllegalStateException("请假命令状态与 expectedStatus 不一致");
        }
        BixiUser actor = currentActor();
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT event_id, status FROM reliable_outbox
                 WHERE source_owner = 'upms' AND type = 'WORKFLOW_START_REQUESTED'
                   AND JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.commandId')) = ?
                   AND %s = ?
                 ORDER BY created_at DESC, event_id DESC LIMIT 1
                """.formatted(normalizedJsonTenantScope("payload_json")),
                commandId, RecoveryTenantScope.currentScope());
        String detail = events.isEmpty() ? "未找到原 START 事件，无法安全重建 Outbox" :
                "原 START 事件状态为 " + text(events.get(0).get("status"));
        String outcome = events.isEmpty() ? "NO_SAFE_REPAIR" : "MATCHED";
        WorkflowRecoveryResultVO value = new WorkflowRecoveryResultVO(request.requestId(), OWNER,
                "COMMAND", commandId, command.status(), command.status(), false, outcome, detail, Instant.now());
        try {
            audit.recordRequest(actor.getId(), "LEAVE_COMMAND_RETRY", OWNER, commandId, request.reason(),
                    request.expectedStatus(), value);
        }
        catch (org.springframework.dao.DuplicateKeyException race) {
            UpmsRecoveryAuditStore.SavedRequest winner = audit.findRequest(OWNER,
                    "LEAVE_COMMAND_RETRY", request.requestId());
            if (winner != null) {
                requireMatchingWinner(request, OWNER, "COMMAND", commandId, winner);
                return winner.result();
            }
            throw race;
        }
        return value;
    }

    public WorkflowRecoveryResultVO reconcileLeaveBooking(String operationId,
            WorkflowRecoveryRequestDTO request) {
        RecoveryTenantScope.requireWritable();
        requireRequest(request);
        WorkflowRecoveryBusinessTaskVO task = leaveBusinessTask(operationId);
        UpmsRecoveryAuditStore.SavedRequest replay = audit.findRequest(OWNER, "LEAVE_BOOKING_RECONCILE",
                request.requestId());
        if (replay != null) {
            requireMatchingWinner(request, OWNER, "BUSINESS_TASK", operationId, replay);
            return replay.result();
        }
        if (!request.expectedStatus().equals(task.status())) {
            throw new IllegalStateException("请假登记状态与 expectedStatus 不一致");
        }
        String outcome = Set.of("BOOKED", "CANCELED").contains(task.status()) ? "MATCHED" : "NO_SAFE_REPAIR";
        String detail = "MATCHED".equals(outcome) ? "请假登记已处于稳定状态"
                : "未发现可由 UPMS owner 安全修复的登记状态转换";
        WorkflowRecoveryResultVO value = new WorkflowRecoveryResultVO(request.requestId(), OWNER,
                "BUSINESS_TASK", operationId, task.status(), task.status(), false, outcome, detail, Instant.now());
        try {
            audit.recordRequest(currentActor().getId(), "LEAVE_BOOKING_RECONCILE", OWNER, operationId,
                    request.reason(), request.expectedStatus(), value);
        }
        catch (org.springframework.dao.DuplicateKeyException race) {
            UpmsRecoveryAuditStore.SavedRequest winner = audit.findRequest(OWNER,
                    "LEAVE_BOOKING_RECONCILE", request.requestId());
            if (winner != null) {
                requireMatchingWinner(request, OWNER, "BUSINESS_TASK", operationId, winner);
                return winner.result();
            }
            throw race;
        }
        return value;
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
            java.util.function.Supplier<DurableMessage> message,
            java.util.function.Function<java.util.function.Consumer<Boolean>, Boolean> operation) {
        requireEventId(eventId);
        requireReason(reason);
        RecoveryTenantScope.requireVisible(message.get());
        BixiUser actor = access.currentUser();
        boolean changed = operation.apply(result ->
                audit.record(actor.getId(), action, OWNER, eventId, null, result, reason));
        return new WorkflowRecoveryActionVO(direction, eventId, changed);
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

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
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

    private BixiUser currentActor() {
        BixiUser actor = access.currentUser();
        if (actor.getTenantId() == null || actor.getTenantId() <= 0 || actor.getId() == null || actor.getId() <= 0) {
            throw new IllegalArgumentException("租户或操作者上下文缺失");
        }
        return actor;
    }

    private static void requireOwner(String owner) {
        if (owner != null && !owner.isBlank() && !OWNER.equals(owner)) {
            throw new IllegalArgumentException("Recovery owner must be upms");
        }
    }

    private static void requireQuery(com.lotus.bixi.workflow.api.dto.WorkflowRecoveryQueryDTO query) {
        if (query == null) throw new IllegalArgumentException("Recovery query is required");
        if (query.getCurrent() < 1 || query.getSize() < 1 || query.getSize() > 200) {
            throw new IllegalArgumentException("current must be positive and size must be between 1 and 200");
        }
        if (query.getStartTime() != null && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new IllegalArgumentException("startTime must not be after endTime");
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
            String resourceType, String resourceId, UpmsRecoveryAuditStore.SavedRequest winner) {
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

    private static void requireRequest(WorkflowRecoveryRequestDTO request) {
        if (request == null) throw new IllegalArgumentException("Recovery request is required");
        requireEventId(request.requestId());
        requireReason(request.reason());
        if (request.expectedStatus() == null || !request.expectedStatus().matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("expectedStatus is invalid");
        }
    }

    private static String eventUnion() {
        String tenantScope = textualJsonTenantScope("payload_json");
        return """
                SELECT 'OUTBOX' AS direction, source_owner AS event_owner, target_owner AS peer_owner,
                       event_id, type AS event_type, schema_version, payload_hash, status AS event_status,
                       attempts, next_attempt_at, lease_until, last_error, created_at,
                       delivered_at AS completed_at,
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessId')) AS UNSIGNED) AS business_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessKey')) AS business_key,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.processInstanceId')) AS process_instance_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.commandId')) AS request_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.operationId')) AS operation_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.compensationId')) AS compensation_id,
                       %1$s AS tenant_scope
                  FROM reliable_outbox
                UNION ALL
                SELECT 'INBOX' AS direction, target_owner AS event_owner, source_owner AS peer_owner,
                       event_id, type AS event_type, schema_version, payload_hash, status AS event_status,
                       attempts, next_attempt_at, lease_until, last_error, received_at AS created_at,
                       processed_at AS completed_at,
                       CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessId')) AS UNSIGNED) AS business_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.businessKey')) AS business_key,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.processInstanceId')) AS process_instance_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.commandId')) AS request_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.operationId')) AS operation_id,
                       JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.payload.compensationId')) AS compensation_id,
                       %1$s AS tenant_scope
                  FROM reliable_inbox
                """.formatted(tenantScope);
    }

    private static String textualJsonTenantScope(String payloadColumn) {
        return "CASE WHEN JSON_TYPE(JSON_EXTRACT(" + payloadColumn + ", '$.tenantScope')) = 'STRING'"
                + " THEN JSON_UNQUOTE(JSON_EXTRACT(" + payloadColumn + ", '$.tenantScope')) END";
    }

    private static String normalizedJsonTenantScope(String payloadColumn) {
        String scope = textualJsonTenantScope(payloadColumn);
        return "CASE WHEN " + scope + " = 'default' THEN '1' ELSE " + scope + " END";
    }

    private static String leaveEventWhere(boolean allTenants) {
        return " WHERE event_owner = ? AND (? IS NULL OR event_type = ?)"
                + " AND (? IS NULL OR event_status = ?) AND (? IS NULL OR direction = ?)"
                + " AND (? IS NULL OR created_at >= ?) AND (? IS NULL OR created_at <= ?)"
                + " AND (? IS NULL OR business_id = ?)"
                + (allTenants
                        ? " AND EXISTS (SELECT 1 FROM demo_leave_request l WHERE l.id = e.business_id"
                                + " AND l.del_flag = '0')"
                        : " AND CASE WHEN e.tenant_scope = 'default' THEN '1' ELSE e.tenant_scope END = ?"
                                + " AND EXISTS (SELECT 1 FROM demo_leave_request l WHERE l.id = e.business_id"
                                + " AND l.applicant_id = ? AND l.tenant_id = ? AND l.del_flag = '0')");
    }

    private static List<Object> leaveEventArgs(BixiUser actor, WorkflowRecoveryQueryDTO query,
            boolean allTenants) {
        List<Object> args = new ArrayList<>();
        args.add(OWNER);
        twice(args, query.getType());
        twice(args, query.getStatus());
        twice(args, query.getDirection());
        twice(args, timestamp(query.getStartTime()));
        twice(args, timestamp(query.getEndTime()));
        twice(args, query.getBusinessId());
        if (!allTenants) {
            args.add(RecoveryTenantScope.currentScope());
            args.add(actor.getId());
            args.add(RecoveryTenantScope.currentTenantId());
        }
        return args;
    }

    private static List<Object> commandArgs(BixiUser actor, WorkflowRecoveryQueryDTO query,
            boolean allTenants) {
        List<Object> args = new ArrayList<>();
        if (!allTenants) {
            args.add(actor.getId());
            args.add(RecoveryTenantScope.currentScope());
        }
        twice(args, query.getType());
        twice(args, query.getStatus());
        twice(args, timestamp(query.getStartTime()));
        twice(args, timestamp(query.getEndTime()));
        twice(args, query.getBusinessId());
        return args;
    }

    private static List<Object> bookingArgs(BixiUser actor, WorkflowRecoveryQueryDTO query,
            boolean allTenants) {
        List<Object> args = new ArrayList<>();
        if (!allTenants) {
            args.add(actor.getId());
            args.add(RecoveryTenantScope.currentTenantId());
        }
        twice(args, query.getStatus());
        twice(args, timestamp(query.getStartTime()));
        twice(args, timestamp(query.getEndTime()));
        twice(args, query.getBusinessId());
        return args;
    }

    private static void twice(List<Object> args, Object value) { args.add(value); args.add(value); }

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
                truncate(text(row.get("last_error"))), instant(row.get("created_at")), instant(row.get("completed_at")));
    }

    private static WorkflowRecoveryCommandVO toCommand(Map<String, Object> row) {
        return new WorkflowRecoveryCommandVO(OWNER, text(row.get("command_id")), text(row.get("request_id")),
                text(row.get("operation")), text(row.get("resource_id")), text(row.get("status")),
                text(row.get("process_instance_id")), number(row.get("business_id")), text(row.get("business_key")),
                number(row.get("actor_id")), truncate(text(row.get("error"))), instant(row.get("created_at")),
                instant(row.get("completed_at")));
    }

    private static WorkflowRecoveryBusinessTaskVO toBusinessTask(Map<String, Object> row) {
        return new WorkflowRecoveryBusinessTaskVO(OWNER, text(row.get("operation_id")),
                text(row.get("process_instance_id")), "demo_leave_request", number(row.get("business_id")),
                integer(row.get("round")), text(row.get("status")), null, text(row.get("compensation_id")),
                null, null, instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        if (value instanceof java.util.Date date) return date.toInstant();
        return Instant.parse(value.toString());
    }

    private static String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 256));
    }

    private static WorkflowRecoveryReconciliationVO toReconciliation(java.util.Map<String, Object> row) {
        String durable = text(row.get("durable_status"));
        String business = text(row.get("business_status"));
        String classification = classify(durable, business);
        return new WorkflowRecoveryReconciliationVO(OWNER, text(row.get("event_id")), text(row.get("event_type")),
                "demo_leave_request", number(row.get("business_id")), text(row.get("business_key")),
                text(row.get("process_instance_id")), integer(row.get("round")), text(row.get("request_id")),
                text(row.get("command_status")), text(row.get("operation_id")),
                text(row.get("business_task_status")), text(row.get("compensation_id")), null, durable, business,
                classification, detail(classification));
    }

    private List<WorkflowRecoveryQuarantine.Evidence> unresolvedQuarantine(int limit) {
        List<WorkflowRecoveryQuarantine.Evidence> evidence = new ArrayList<>();
        DurableMessageWireCodec codec = new DurableMessageWireCodec();
        long offset = 0;
        while (evidence.size() < limit) {
            List<JdbcQuarantineStore.Snapshot> page = quarantine.listPage(200, offset);
            for (JdbcQuarantineStore.Snapshot snapshot : page) {
                DurableMessage message;
                try {
                    message = codec.decode(snapshot.bodyJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                catch (RuntimeException invalidWire) {
                    continue;
                }
                if (!OWNER.equals(message.targetOwner()) || !RecoveryTenantScope.isVisible(message)
                        || resolved(message.eventId())) {
                    continue;
                }
                evidence.add(new WorkflowRecoveryQuarantine.Evidence(message.eventId(), message.type(),
                        snapshot.reason(), WorkflowRecoveryMetadata.parse(message.payloadJson()).orElse(null)));
                if (evidence.size() == limit) break;
            }
            if (evidence.size() == limit || page.size() < 200) break;
            offset = Math.addExact(offset, page.size());
        }
        return List.copyOf(evidence);
    }

    private static DurableMessage quarantinedMessage(JdbcQuarantineStore.Snapshot snapshot) {
        if (snapshot.bodyJson() == null || snapshot.bodyJson().isBlank()) return null;
        try {
            return new DurableMessageWireCodec().decode(snapshot.bodyJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (RuntimeException invalidWire) {
            return null;
        }
    }

    private boolean resolved(String eventId) {
        JdbcInboxStore.Snapshot received = inbox.find(OWNER, eventId);
        return received != null && (received.state() == JdbcInboxStore.State.PROCESSED
                || received.state() == JdbcInboxStore.State.IGNORED);
    }

    static String classify(String durableStatus, String businessStatus) {
        if ("FAILED".equals(durableStatus)) return "FAILED_DELIVERY";
        if (durableStatus == null && "SUBMITTING".equals(businessStatus)) return "PENDING_DELIVERY";
        if (durableStatus != null && businessStatus != null) return "MATCHED";
        return "BUSINESS_MISMATCH";
    }

    private static String detail(String classification) {
        return switch (classification) {
            case "PENDING_DELIVERY" -> "业务已预留，尚未找到可交付的 UPMS Outbox 记录";
            case "FAILED_DELIVERY" -> "最近一条 UPMS Outbox 投递失败，需要重试或人工核查";
            case "MATCHED" -> "业务状态与 durable 投递记录均存在";
            default -> "业务记录与 durable 记录无法完成关联";
        };
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static Long number(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static Integer integer(Object value) { return value == null ? null : ((Number) value).intValue(); }
}
