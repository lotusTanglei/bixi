package com.lotus.bixi.upms.demo.leave.event;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.upms.demo.leave.service.LeaveBookingService;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationRequested;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Applies workflow-owned automatic-task commands to the UPMS booking state machine. */
public final class LeaveBusinessTaskEventHandler implements DurableMessageHandler {
    private static final String PROCESS_KEY = "demo_leave_approval";
    private static final String BUSINESS_TABLE = "demo_leave_request";

    private final LeaveRequestMapper leaves;
    private final LeaveBookingService bookings;
    private final Outbox outbox;
    private final WorkflowEventCodec codec;

    public LeaveBusinessTaskEventHandler(LeaveRequestMapper leaves, LeaveBookingService bookings,
            @Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
        this(leaves, bookings, outbox::enqueue, codec);
    }

    LeaveBusinessTaskEventHandler(LeaveRequestMapper leaves, LeaveBookingService bookings,
            Outbox outbox, WorkflowEventCodec codec) {
        this.leaves = leaves;
        this.bookings = bookings;
        this.outbox = outbox;
        this.codec = codec;
    }

    @Override
    public Result handle(DurableMessage message) {
        WorkflowEvent event = decode(message);
        validateEnvelope(message, event);
        if (event.type() != WorkflowEventType.WORKFLOW_BUSINESS_TASK_REQUESTED
                && event.type() != WorkflowEventType.WORKFLOW_COMPENSATION_REQUESTED) {
            throw permanent("UPMS 收到未支持的自动任务事件");
        }
        Long previousTenant = TenantContextHolder.get();
        try {
            TenantContextHolder.set(SecurityConstants.DEFAULT_TENANT_ID);
            // Lock the aggregate before touching the booking table. Both booking and
            // compensation use this same lock order, preventing MySQL gap-lock deadlocks.
            LeaveRequest leave = leaves.selectByIdForUpdate(event.businessId());
            validateIdentity(leave, event);
            if (event.round() < leave.getRound()) return Result.IGNORED;
            if (event.round() > leave.getRound()) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                        "自动任务事件属于尚未创建的申请轮次");
            }
            if (event.payload() instanceof WorkflowBusinessTaskRequested requested) {
                return request(leave, event, requested);
            }
            return compensate(leave, event, (WorkflowCompensationRequested) event.payload());
        }
        finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }

    private Result request(LeaveRequest leave, WorkflowEvent event,
                           WorkflowBusinessTaskRequested requested) {
        if (!Objects.equals(leave.getStartRequestHash(), requested.requestHash())) {
            throw permanent("自动任务事件摘要冲突");
        }
        LeaveBookingService.Result booking;
        try {
            booking = bookings.request(requested.operationId(), leave.getId(), event.round(), requested.requestHash());
        }
        catch (IllegalStateException conflict) {
            enqueueTaskResult(event, requested.operationId(), false, null, "BOOKING_CONFLICT");
            return Result.PROCESSED;
        }
        boolean success = "BOOKED".equals(booking.state());
        enqueueTaskResult(event, requested.operationId(), success,
                success ? booking.bookingReference() : null, success ? null : "BOOKING_CANCELED");
        return Result.PROCESSED;
    }

    private Result compensate(LeaveRequest leave, WorkflowEvent event,
                               WorkflowCompensationRequested requested) {
        if (!Objects.equals(leave.getStartRequestHash(), requested.requestHash())) {
            throw permanent("补偿事件摘要冲突");
        }
        LeaveBookingService.Result compensation;
        try {
            compensation = bookings.compensate(requested.operationId(), leave.getId(), event.round(),
                    requested.requestHash(), requested.compensationId());
        }
        catch (IllegalStateException conflict) {
            enqueueCompensationResult(event, requested.operationId(), requested.compensationId(), false,
                    "COMPENSATION_CONFLICT");
            return Result.PROCESSED;
        }
        enqueueCompensationResult(event, requested.operationId(), requested.compensationId(),
                "CANCELED".equals(compensation.state()), null);
        return Result.PROCESSED;
    }

    private void enqueueTaskResult(WorkflowEvent request, String operationId, boolean success,
                                    String bookingReference, String errorCode) {
        WorkflowEvent result = new WorkflowEvent(resultId(operationId, "result"),
                WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT, 1, "upms", "workflow", "default",
                request.processInstanceId(), PROCESS_KEY, BUSINESS_TABLE, request.businessId(), request.businessKey(),
                request.round(), request.commandId(), nextSequence(request), Instant.now(), request.correlationId(),
                request.eventId(), request.actor(), new WorkflowBusinessTaskResult(request.payload().requestHash(),
                        operationId, success, bookingReference, errorCode, Instant.now()));
        enqueue(result, "business-task:" + operationId + ":result");
    }

    private void enqueueCompensationResult(WorkflowEvent request, String operationId, String compensationId,
                                           boolean success, String errorCode) {
        WorkflowEvent result = new WorkflowEvent(resultId(compensationId, "result"),
                WorkflowEventType.WORKFLOW_COMPENSATION_RESULT, 1, "upms", "workflow", "default",
                request.processInstanceId(), PROCESS_KEY, BUSINESS_TABLE, request.businessId(), request.businessKey(),
                request.round(), request.commandId(), nextSequence(request), Instant.now(), request.correlationId(),
                request.eventId(), request.actor(), new WorkflowCompensationResult(request.payload().requestHash(),
                        operationId, compensationId, success, errorCode, Instant.now()));
        enqueue(result, "compensation:" + compensationId + ":result");
    }

    private void enqueue(WorkflowEvent event, String dedupKey) {
        String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
        outbox.enqueue(DurableMessage.create("upms", "workflow", event.eventId(), event.type().name(), 1, payload),
                dedupKey, event.businessKey(), event.aggregateSequence());
    }

    private static int nextSequence(WorkflowEvent event) {
        return Math.max(2, Math.toIntExact(event.aggregateSequence() + 1));
    }

    private static String resultId(String identity, String suffix) {
        return UUID.nameUUIDFromBytes((identity + ":" + suffix).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void validateIdentity(LeaveRequest leave, WorkflowEvent event) {
        if (leave == null) throw permanent("请假申请不存在");
        if (!PROCESS_KEY.equals(event.processKey()) || !BUSINESS_TABLE.equals(event.businessTable())
                || !Objects.equals(leave.getBusinessKey(), event.businessKey())
                || !Objects.equals(leave.getProcessInstanceId(), event.processInstanceId())
                || event.actor() == null || !"upms".equals(event.actor().originatingService())
                || (!Objects.equals(leave.getApplicantId(), event.actor().userId())
                && !Objects.equals(leave.getApproverId(), event.actor().userId()))) {
            throw permanent("自动任务事件与请假申请身份不匹配");
        }
    }

    private WorkflowEvent decode(DurableMessage message) {
        try {
            return codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException invalid) {
            throw permanent("自动任务事件载荷无效");
        }
    }

    private static void validateEnvelope(DurableMessage message, WorkflowEvent event) {
        if (!message.eventId().equals(event.eventId()) || !message.type().equals(event.type().name())
                || message.schemaVersion() != event.schemaVersion()
                || !message.sourceOwner().equals(event.sourceOwner())
                || !message.targetOwner().equals(event.targetOwner())) {
            throw permanent("自动任务事件信封与载荷不一致");
        }
        if (!"workflow".equals(event.sourceOwner()) || !"upms".equals(event.targetOwner())) {
            throw permanent("自动任务事件路由无效");
        }
    }

    private static InboxDeliveryException permanent(String message) {
        return new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, message);
    }

    @FunctionalInterface
    interface Outbox {
        void enqueue(DurableMessage message, String dedupKey, String aggregateKey, Long aggregateSequence);
    }
}
