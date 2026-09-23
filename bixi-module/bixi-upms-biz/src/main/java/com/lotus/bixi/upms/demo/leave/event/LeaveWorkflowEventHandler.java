package com.lotus.bixi.upms.demo.leave.event;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper;
import com.lotus.bixi.workflow.api.event.WorkflowCompleted;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.api.event.WorkflowStartRejected;
import com.lotus.bixi.workflow.api.event.WorkflowStarted;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** Applies workflow lifecycle events to leave requests in the UPMS inbox transaction. */
public final class LeaveWorkflowEventHandler implements DurableMessageHandler {
    private static final String PROCESS_KEY = "demo_leave_approval";
    private static final String BUSINESS_TABLE = "demo_leave_request";

    private final LeaveRequestMapper leaves;
    private final WorkflowEventCodec codec;

    public LeaveWorkflowEventHandler(LeaveRequestMapper leaves,
            @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
        this.leaves = leaves;
        this.codec = codec;
    }

    @Override
    public Result handle(DurableMessage message) {
        WorkflowEvent event = decode(message);
        validateEnvelope(message, event);
        validate(event);
        Long previousTenant = TenantContextHolder.get();
        try {
            TenantContextHolder.set(SecurityConstants.DEFAULT_TENANT_ID);
            LeaveRequest leave = leaves.selectById(event.businessId());
            if (leave == null) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, "请假申请不存在");
            }
            if (!Objects.equals(leave.getBusinessKey(), event.businessKey())
                    || !trustedActor(leave, event)) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, "流程事件与请假申请身份不匹配");
            }
            if (event.round() < leave.getRound()) return Result.IGNORED;
            if (event.round() > leave.getRound()) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                        "工作流事件属于尚未创建的申请轮次");
            }
            return switch (event.type()) {
                case WORKFLOW_STARTED -> applyStarted(leave, event);
                case WORKFLOW_START_REJECTED -> applyRejected(leave, event);
                case WORKFLOW_COMPLETED -> applyCompleted(leave, event);
                default -> throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                        "UPMS 收到未支持的工作流事件");
            };
        }
        finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }

    private Result applyStarted(LeaveRequest leave, WorkflowEvent event) {
        if (!(event.payload() instanceof WorkflowStarted started) || event.processInstanceId() == null) {
            throw permanent("启动成功事件无效");
        }
        requireHash(leave, started.requestHash());
        requireCommand(leave, event.commandId());
        if (leave.getProcessInstanceId() != null) {
            if (leave.getProcessInstanceId().equals(event.processInstanceId())) return Result.IGNORED;
            throw permanent("请假申请已绑定其他流程");
        }
        if (!"SUBMITTING".equals(leave.getLeaveStatus())) return Result.IGNORED;
        int updated = leaves.update(null, Wrappers.<LeaveRequest>lambdaUpdate()
                .eq(LeaveRequest::getId, leave.getId())
                .eq(LeaveRequest::getRound, event.round())
                .eq(LeaveRequest::getLeaveStatus, "SUBMITTING")
                .isNull(LeaveRequest::getProcessInstanceId)
                .set(LeaveRequest::getProcessInstanceId, event.processInstanceId())
                .set(LeaveRequest::getStartCommandId, event.commandId())
                .set(LeaveRequest::getStartRequestHash, started.requestHash())
                .set(LeaveRequest::getLeaveStatus, "IN_REVIEW"));
        return updated == 1 ? Result.PROCESSED : Result.IGNORED;
    }

    private Result applyRejected(LeaveRequest leave, WorkflowEvent event) {
        if (!(event.payload() instanceof WorkflowStartRejected rejected)) {
            throw permanent("启动拒绝事件无效");
        }
        requireHash(leave, rejected.requestHash());
        requireCommand(leave, event.commandId());
        if (leave.getProcessInstanceId() != null) return Result.IGNORED;
        if (!"SUBMITTING".equals(leave.getLeaveStatus())) {
            if ("REJECTED".equals(leave.getLeaveStatus())) return Result.IGNORED;
            throw permanent("启动拒绝事件与当前状态冲突");
        }
        int updated = leaves.update(null, Wrappers.<LeaveRequest>lambdaUpdate()
                .eq(LeaveRequest::getId, leave.getId())
                .eq(LeaveRequest::getRound, event.round())
                .eq(LeaveRequest::getLeaveStatus, "SUBMITTING")
                .isNull(LeaveRequest::getProcessInstanceId)
                .set(LeaveRequest::getStartCommandId, event.commandId())
                .set(LeaveRequest::getStartRequestHash, rejected.requestHash())
                .set(LeaveRequest::getLeaveStatus, "REJECTED")
                .set(LeaveRequest::getEndedAt, LocalDateTime.now()));
        return updated == 1 ? Result.PROCESSED : Result.IGNORED;
    }

    private Result applyCompleted(LeaveRequest leave, WorkflowEvent event) {
        if (!(event.payload() instanceof WorkflowCompleted completed) || event.processInstanceId() == null) {
            throw permanent("流程完成事件无效");
        }
        requireHash(leave, completed.requestHash());
        requireCommand(leave, event.commandId());
        String current = leave.getProcessInstanceId();
        if (current != null && !current.equals(event.processInstanceId())) {
            throw permanent("流程完成事件与已绑定实例不匹配");
        }
        if (isTerminal(leave.getLeaveStatus())) {
            if (current != null && leave.getLeaveStatus().equals(completed.outcome().name())) {
                return Result.IGNORED;
            }
            throw permanent("流程完成事件与已结束状态冲突");
        }
        if (!"SUBMITTING".equals(leave.getLeaveStatus()) && !"IN_REVIEW".equals(leave.getLeaveStatus())) {
            throw permanent("流程完成事件与当前状态冲突");
        }
        int updated = leaves.update(null, Wrappers.<LeaveRequest>lambdaUpdate()
                .eq(LeaveRequest::getId, leave.getId())
                .eq(LeaveRequest::getRound, event.round())
                .eq(LeaveRequest::getLeaveStatus, leave.getLeaveStatus())
                .set(LeaveRequest::getProcessInstanceId, event.processInstanceId())
                .set(LeaveRequest::getStartCommandId, event.commandId())
                .set(LeaveRequest::getStartRequestHash, completed.requestHash())
                .set(LeaveRequest::getLeaveStatus, completed.outcome().name())
                .set(LeaveRequest::getEndedAt, LocalDateTime.ofInstant(completed.endedAt(), ZoneId.systemDefault())));
        return updated == 1 ? Result.PROCESSED : Result.IGNORED;
    }

    private static void validate(WorkflowEvent event) {
        if (!PROCESS_KEY.equals(event.processKey()) || !BUSINESS_TABLE.equals(event.businessTable())
                || !"workflow".equals(event.sourceOwner()) || !"upms".equals(event.targetOwner())) {
            throw permanent("工作流事件路由无效");
        }
    }

    private static void validateEnvelope(DurableMessage message, WorkflowEvent event) {
        if (!message.eventId().equals(event.eventId()) || !message.type().equals(event.type().name())
                || message.schemaVersion() != event.schemaVersion()
                || !message.sourceOwner().equals(event.sourceOwner())
                || !message.targetOwner().equals(event.targetOwner())) {
            throw permanent("工作流事件信封与载荷不一致");
        }
    }

    private static boolean trustedActor(LeaveRequest leave, WorkflowEvent event) {
        if (event.actor() == null || event.actor().userId() <= 0) return false;
        return switch (event.type()) {
            case WORKFLOW_STARTED, WORKFLOW_START_REJECTED ->
                    "upms".equals(event.actor().originatingService())
                            && Objects.equals(leave.getApplicantId(), event.actor().userId());
            case WORKFLOW_COMPLETED -> "upms".equals(event.actor().originatingService())
                    && (Objects.equals(leave.getApplicantId(), event.actor().userId())
                    || Objects.equals(leave.getApproverId(), event.actor().userId()));
            default -> false;
        };
    }

    private WorkflowEvent decode(DurableMessage message) {
        try {
            return codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException invalid) {
            throw permanent("工作流事件载荷无效");
        }
    }

    private static void requireHash(LeaveRequest leave, String requestHash) {
        if (leave.getStartRequestHash() != null && !leave.getStartRequestHash().equals(requestHash)) {
            throw permanent("工作流事件摘要冲突");
        }
    }

    private static void requireCommand(LeaveRequest leave, String commandId) {
        if (leave.getStartCommandId() != null && !leave.getStartCommandId().equals(commandId)) {
            throw permanent("工作流事件命令标识冲突");
        }
    }

    private static boolean isTerminal(String status) {
        return "APPROVED".equals(status) || "REJECTED".equals(status) || "CANCELED".equals(status);
    }

    private static InboxDeliveryException permanent(String message) {
        return new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, message);
    }
}
