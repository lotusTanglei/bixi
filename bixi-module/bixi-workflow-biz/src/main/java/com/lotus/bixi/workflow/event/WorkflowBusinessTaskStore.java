package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Durable workflow-side state for external business-task operations. */
public final class WorkflowBusinessTaskStore implements WorkflowBusinessTaskResultStore {

    private static final String BUSINESS_OWNER = "upms";
    private static final String BUSINESS_TABLE = "demo_leave_request";

    private final JdbcTemplate jdbc;

    public WorkflowBusinessTaskStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "DataSource is required"));
    }

    /** Idempotently reserves the operation in the same transaction that emits its outbox event. */
    public void createWaiting(WorkflowBusinessTaskEventPublisher.Context context) {
        requireContext(context);
        try {
            jdbc.update("""
                    INSERT INTO wf_business_task
                        (operation_id, process_instance_id, execution_id, activity_id, activity_occurrence,
                         business_owner, business_table, business_id, business_round, request_hash,
                         tenant_scope, status, deadline, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WAITING', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                    """, context.operationId(), context.processInstanceId(), context.executionId(),
                    context.activityId(), context.activityOccurrence(), BUSINESS_OWNER, BUSINESS_TABLE,
                    context.businessId(), context.round(), context.requestHash(), context.actor().tenantScope(),
                    Timestamp.from(context.deadline()));
        }
        catch (DuplicateKeyException duplicate) {
            Snapshot saved = findByIdentity(context);
            if (saved == null || !matches(saved, context)) {
                throw permanent("自动任务身份与已持久化操作冲突");
            }
        }
    }

    /** Record a business result before the matching receive task is advanced. */
    public boolean recordBusinessResult(WorkflowEvent event, WorkflowBusinessTaskResult result) {
        Snapshot saved = requireMatching(event, result.operationId(), result.requestHash());
        if ("WAITING".equals(saved.status())) {
            String next = result.success() ? "SUCCEEDED" : "FAILED";
            return jdbc.update("""
                    UPDATE wf_business_task
                       SET status = ?, result_event_id = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP(6)
                     WHERE operation_id = ? AND status = 'WAITING'
                    """, next, event.eventId(), bounded(result.errorCode()), result.operationId()) == 1;
        }
        if (isLateBusinessResult(saved.status())) {
            // A timeout/compensation transition owns the workflow state. Keep the
            // late wire event observable, but never let a stale receive execution
            // reopen the process or overwrite that transition.
            rememberResultEvent(saved, event.eventId());
            return false;
        }
        if ((result.success() && "SUCCEEDED".equals(saved.status()))
                || (!result.success() && "FAILED".equals(saved.status()))) {
            // A redelivery of the exact event may follow a durable write that was
            // committed before the receive task advanced. Let the handler retry
            // the engine transition, while a different event for the same outcome
            // remains an ignored duplicate.
            return event.eventId().equals(saved.resultEventId());
        }
        throw permanent("自动任务结果状态转换无效");
    }

    /**
     * Classify a result after Flowable has already removed the receive execution. This
     * closes the result-loss window where the business row committed first and a later
     * timeout/termination made the runtime query empty. WAITING remains retryable because
     * there is still work to recover; late and terminal states are durable acknowledgements.
     */
    @Override
    public boolean recordBusinessResultWithoutExecution(WorkflowEvent event,
            WorkflowBusinessTaskResult result) {
        Snapshot saved = requireMatching(event, result.operationId(), result.requestHash());
        String status = saved.status();
        if (isLateBusinessResult(status)) {
            rememberResultEvent(saved, event.eventId());
            return true;
        }
        if ("SUCCEEDED".equals(status)) {
            if (!result.success()) throw permanent("自动任务成功结果与已持久化状态冲突");
            rememberResultEvent(saved, event.eventId());
            return true;
        }
        if ("FAILED".equals(status)) {
            if (result.success()) throw permanent("自动任务失败结果与已持久化状态冲突");
            rememberResultEvent(saved, event.eventId());
            return true;
        }
        return false;
    }

    /** Move a failed or timed-out operation into the compensation phase. */
    public boolean markCompensating(WorkflowBusinessTaskEventPublisher.Context context,
                                    String compensationId, boolean timedOut) {
        requireContext(context);
        requireId(compensationId, "compensationId");
        Snapshot saved = findForUpdate(context.operationId());
        if (saved == null || !matchesOperation(saved, context)) {
            throw permanent("补偿请求与自动任务身份冲突");
        }
        if ("COMPENSATING".equals(saved.status()) || "COMPENSATED".equals(saved.status())) {
            if (!compensationId.equals(saved.compensationId())) {
                throw permanent("自动任务补偿标识冲突");
            }
            return false;
        }
        if (!("WAITING".equals(saved.status()) || "FAILED".equals(saved.status())
                || "TIMED_OUT".equals(saved.status()))) {
            throw permanent("自动任务不能进入补偿状态");
        }
        return jdbc.update("""
                UPDATE wf_business_task
                   SET status = 'COMPENSATING', compensation_id = ?,
                       last_error = CASE WHEN ? THEN 'AUTO_TASK_TIMEOUT' ELSE last_error END,
                       updated_at = CURRENT_TIMESTAMP(6)
                 WHERE operation_id = ? AND status IN ('WAITING', 'FAILED', 'TIMED_OUT')
                """, compensationId, timedOut, context.operationId()) == 1;
    }

    /** Record a compensation result before the compensation receive task is advanced. */
    public boolean recordCompensationResult(WorkflowEvent event, WorkflowCompensationResult result) {
        Snapshot saved = requireMatching(event, result.operationId(), result.requestHash());
        if (!result.compensationId().equals(saved.compensationId())) {
            throw permanent("自动任务补偿结果标识冲突");
        }
        if ("COMPENSATING".equals(saved.status())) {
            String next = result.success() ? "COMPENSATED" : "FAILED";
            return jdbc.update("""
                    UPDATE wf_business_task
                       SET status = ?, result_event_id = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP(6)
                     WHERE operation_id = ? AND status = 'COMPENSATING' AND compensation_id = ?
                    """, next, event.eventId(), bounded(result.errorCode()), result.operationId(),
                    result.compensationId()) == 1;
        }
        if ((result.success() && "COMPENSATED".equals(saved.status()))
                || (!result.success() && "FAILED".equals(saved.status()))) {
            return event.eventId().equals(saved.resultEventId());
        }
        throw permanent("自动任务补偿结果状态转换无效");
    }

    /** Classify a compensation result after the receive execution is gone. */
    @Override
    public boolean recordCompensationResultWithoutExecution(WorkflowEvent event,
            WorkflowCompensationResult result) {
        Snapshot saved = requireMatching(event, result.operationId(), result.requestHash());
        if (!result.compensationId().equals(saved.compensationId())) {
            throw permanent("自动任务补偿结果标识冲突");
        }
        if ("COMPENSATED".equals(saved.status())) {
            if (!result.success()) throw permanent("已补偿任务不能接受失败补偿结果");
            rememberResultEvent(saved, event.eventId());
            return true;
        }
        if ("FAILED".equals(saved.status()) && saved.compensationId() != null) {
            if (result.success()) throw permanent("补偿失败状态不能接受成功补偿结果");
            rememberResultEvent(saved, event.eventId());
            return true;
        }
        return false;
    }

    public Snapshot find(String operationId) {
        requireId(operationId, "operationId");
        List<Snapshot> rows = jdbc.query("SELECT * FROM wf_business_task WHERE operation_id = ?",
                (row, index) -> read(row), operationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Snapshot requireMatching(WorkflowEvent event, String operationId, String requestHash) {
        Objects.requireNonNull(event, "Workflow event is required");
        Snapshot saved = findForUpdate(operationId);
        if (saved == null
                || !event.processInstanceId().equals(saved.processInstanceId())
                || event.businessId() != saved.businessId()
                || event.round() != saved.businessRound()
                || !event.tenantScope().equals(saved.tenantScope())
                || !requestHash.equals(saved.requestHash())) {
            throw permanent("自动任务结果与持久化操作不匹配");
        }
        return saved;
    }

    private Snapshot findByIdentity(WorkflowBusinessTaskEventPublisher.Context context) {
        List<Snapshot> rows = jdbc.query("""
                SELECT * FROM wf_business_task
                 WHERE operation_id = ?
                    OR (process_instance_id = ? AND activity_id = ? AND activity_occurrence = ?)
                 FOR UPDATE
                """, (row, index) -> read(row), context.operationId(), context.processInstanceId(),
                context.activityId(), context.activityOccurrence());
        return rows.size() == 1 ? rows.get(0) : null;
    }

    private Snapshot findForUpdate(String operationId) {
        requireId(operationId, "operationId");
        List<Snapshot> rows = jdbc.query("SELECT * FROM wf_business_task WHERE operation_id = ? FOR UPDATE",
                (row, index) -> read(row), operationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void rememberResultEvent(Snapshot saved, String eventId) {
        if (saved.resultEventId() != null) return;
        jdbc.update("""
                UPDATE wf_business_task
                   SET result_event_id = ?, updated_at = CURRENT_TIMESTAMP(6)
                 WHERE operation_id = ? AND result_event_id IS NULL
                """, eventId, saved.operationId());
    }

    private static boolean matches(Snapshot saved, WorkflowBusinessTaskEventPublisher.Context context) {
        return matchesOperation(saved, context)
                && context.processInstanceId().equals(saved.processInstanceId())
                && context.executionId().equals(saved.executionId())
                && context.activityId().equals(saved.activityId())
                && context.activityOccurrence() == saved.activityOccurrence();
    }

    private static boolean matchesOperation(Snapshot saved, WorkflowBusinessTaskEventPublisher.Context context) {
        return context.operationId().equals(saved.operationId())
                && context.processInstanceId().equals(saved.processInstanceId())
                && context.businessId() == saved.businessId()
                && context.round() == saved.businessRound()
                && context.requestHash().equals(saved.requestHash())
                && context.actor().tenantScope().equals(saved.tenantScope());
    }

    private static boolean isLateBusinessResult(String status) {
        return "TIMED_OUT".equals(status) || "COMPENSATING".equals(status)
                || "COMPENSATED".equals(status) || "CANCELED".equals(status);
    }

    private static void requireContext(WorkflowBusinessTaskEventPublisher.Context context) {
        Objects.requireNonNull(context, "Business-task context is required");
        requireId(context.operationId(), "operationId");
        requireId(context.processInstanceId(), "processInstanceId");
        requireId(context.executionId(), "executionId");
        requireId(context.activityId(), "activityId");
        requireId(context.requestHash(), "requestHash");
        if (context.activityOccurrence() < 1 || context.businessId() < 1 || context.round() < 1
                || context.deadline() == null || context.actor() == null
                || context.actor().tenantScope() == null || context.actor().tenantScope().isBlank()) {
            throw new IllegalArgumentException("自动任务上下文不完整");
        }
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String bounded(String value) {
        if (value == null) return null;
        return value.substring(0, Math.min(value.length(), 128));
    }

    private static InboxDeliveryException permanent(String message) {
        return new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, message);
    }

    private static Snapshot read(ResultSet row) throws SQLException {
        Timestamp deadline = row.getTimestamp("deadline");
        return new Snapshot(row.getString("operation_id"), row.getString("process_instance_id"),
                row.getString("execution_id"), row.getString("activity_id"),
                row.getInt("activity_occurrence"), row.getLong("business_id"),
                row.getInt("business_round"), row.getString("request_hash"),
                row.getString("tenant_scope"), row.getString("status"),
                deadline == null ? null : deadline.toInstant(), row.getString("result_event_id"),
                row.getString("compensation_id"), row.getString("last_error"));
    }

    public record Snapshot(String operationId, String processInstanceId, String executionId,
                           String activityId, int activityOccurrence, long businessId,
                           int businessRound, String requestHash, String tenantScope,
                           String status, Instant deadline, String resultEventId,
                           String compensationId, String lastError) { }
}
