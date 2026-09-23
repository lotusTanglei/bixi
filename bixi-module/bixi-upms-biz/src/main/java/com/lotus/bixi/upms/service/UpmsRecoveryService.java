package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableMessageWireCodec;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.event.WorkflowRecoveryMetadata;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
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

    /** Correlates UPMS leave rows with the latest durable command metadata without exposing payloads. */
    public List<WorkflowRecoveryReconciliationVO> reconcile(int limit) {
        requireLimit(limit);
        if (jdbc == null) {
            throw new IllegalStateException("Recovery reconciliation requires a DataSource");
        }
        List<WorkflowRecoveryReconciliationVO> businessRows = jdbc.queryForList("""
                SELECT l.id AS business_id, l.business_key, l.process_instance_id, l.round,
                       l.leave_status AS business_status, l.start_command_id AS request_id,
                       (SELECT o.status FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND o.type = 'WORKFLOW_START_REQUESTED'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                          ORDER BY o.created_at DESC LIMIT 1) AS command_status,
                        (SELECT o.event_id FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                          ORDER BY o.created_at DESC LIMIT 1) AS event_id,
                       (SELECT o.type FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                          ORDER BY o.created_at DESC LIMIT 1) AS event_type,
                       (SELECT o.status FROM reliable_outbox o
                          WHERE o.source_owner = 'upms'
                            AND JSON_UNQUOTE(JSON_EXTRACT(o.payload_json, '$.businessKey')) = l.business_key
                          ORDER BY o.created_at DESC LIMIT 1) AS durable_status,
                       b.operation_id, b.booking_state AS business_task_status, b.compensation_id
                  FROM demo_leave_request l
                  LEFT JOIN demo_leave_booking b ON b.leave_id = l.id AND b.round = l.round
                 WHERE l.del_flag = '0'
                 ORDER BY l.update_time DESC, l.id DESC
                 LIMIT ?
                """, limit).stream().map(UpmsRecoveryService::toReconciliation).toList();
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
            evidence.add(new WorkflowRecoveryQuarantine.Evidence(message.eventId(), message.type(),
                    snapshot.reason(), WorkflowRecoveryMetadata.parse(message.payloadJson()).orElse(null)));
        }
        return List.copyOf(evidence);
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
