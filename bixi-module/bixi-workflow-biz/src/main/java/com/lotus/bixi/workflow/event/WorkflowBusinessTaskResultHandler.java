package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskResult;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationResult;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Applies an UPMS result to the matching Flowable receive task. */
public final class WorkflowBusinessTaskResultHandler implements DurableMessageHandler {
    private final RuntimeService runtime;
    private final HistoryService history;
    private final WorkflowEventCodec codec;

    public WorkflowBusinessTaskResultHandler(RuntimeService runtime,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        this(runtime, null, codec);
    }

    public WorkflowBusinessTaskResultHandler(RuntimeService runtime, HistoryService history,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        this.runtime = runtime;
        this.history = history;
        this.codec = codec;
    }

    @Override
    public Result handle(DurableMessage message) {
        WorkflowEvent event = decode(message);
        validateEnvelope(message, event);
        Long previousTenant = TenantContextHolder.get();
        try {
            TenantContextHolder.set(SecurityConstants.DEFAULT_TENANT_ID);
            if (event.type() == WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT) {
                WorkflowBusinessTaskResult result = (WorkflowBusinessTaskResult) event.payload();
                return apply(event, result.operationId(), "waitBusinessResult", "businessTaskResultOperationId",
                        result.success(), result.bookingReference(), result.errorCode());
            }
            WorkflowCompensationResult result = (WorkflowCompensationResult) event.payload();
            return apply(event, result.operationId(), "waitCompensationResult", "compensationResultOperationId",
                    result.success(), null, result.errorCode());
        }
        finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }

    private Result apply(WorkflowEvent event, String operationId, String activityId, String completedVariable,
                         boolean success, String bookingReference, String errorCode) {
        Execution execution = runtime.createExecutionQuery()
                .processInstanceId(event.processInstanceId())
                .activityId(activityId)
                .list().stream()
                .filter(candidate -> operationId.equals(runtime.getVariable(candidate.getId(), "businessOperationId")))
                .findFirst().orElse(null);
        if (execution == null) {
            Object prior = runtimeVariable(event.processInstanceId(), completedVariable);
            if (operationId.equals(prior)) return Result.IGNORED;
            if ("waitBusinessResult".equals(activityId)
                    && waitingForCompensation(event.processInstanceId(), operationId)) {
                // The timeout/failed-result branch has already won. A business result that
                // arrives afterwards is validly late, not retryable work and must not reopen
                // the receive task or overwrite the compensation state.
                return Result.IGNORED;
            }
            if (operationId.equals(historicVariable(event.processInstanceId(), completedVariable))
                    || operationId.equals(historicVariable(event.processInstanceId(), terminalCounterpart(completedVariable)))) {
                return Result.IGNORED;
            }
            throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                    "自动任务 receive task 尚未可恢复");
        }
        Object expectedHash = runtime.getVariable(execution.getId(), "startRequestHash");
        if (expectedHash != null && !event.payload().requestHash().equals(expectedHash)) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, "自动任务结果摘要冲突");
        }
        Map<String, Object> variables = new HashMap<>();
        variables.put(completedVariable, operationId);
        variables.put("businessTaskSuccess", success);
        if (bookingReference != null) variables.put("bookingReference", bookingReference);
        if (errorCode != null) variables.put("businessTaskErrorCode", errorCode);
        runtime.setVariables(execution.getId(), variables);
        runtime.trigger(execution.getId());
        return Result.PROCESSED;
    }

    private boolean waitingForCompensation(String processInstanceId, String operationId) {
        return runtime.createExecutionQuery().processInstanceId(processInstanceId)
                .activityId("waitCompensationResult").list().stream()
                .anyMatch(candidate -> operationId.equals(runtime.getVariable(candidate.getId(), "businessOperationId")));
    }

    private Object runtimeVariable(String processInstanceId, String variableName) {
        try {
            return runtime.getVariable(processInstanceId, variableName);
        }
        catch (org.flowable.common.engine.api.FlowableObjectNotFoundException ended) {
            return null;
        }
    }

    private Object historicVariable(String processInstanceId, String variableName) {
        if (history == null) return null;
        return history.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId).variableName(variableName).list().stream()
                .map(org.flowable.variable.api.history.HistoricVariableInstance::getValue)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static String terminalCounterpart(String completedVariable) {
        return "businessTaskResultOperationId".equals(completedVariable)
                ? "compensationResultOperationId" : "businessTaskResultOperationId";
    }

    private WorkflowEvent decode(DurableMessage message) {
        try {
            return codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException invalid) {
            throw permanent("自动任务结果载荷无效");
        }
    }

    private static void validateEnvelope(DurableMessage message, WorkflowEvent event) {
        if (!message.eventId().equals(event.eventId()) || !message.type().equals(event.type().name())
                || message.schemaVersion() != event.schemaVersion()
                || !message.sourceOwner().equals(event.sourceOwner())
                || !message.targetOwner().equals(event.targetOwner())) {
            throw permanent("自动任务结果信封与载荷不一致");
        }
        if (!"upms".equals(event.sourceOwner()) || !"workflow".equals(event.targetOwner())
                || (event.type() != WorkflowEventType.WORKFLOW_BUSINESS_TASK_RESULT
                && event.type() != WorkflowEventType.WORKFLOW_COMPENSATION_RESULT)) {
            throw permanent("自动任务结果路由无效");
        }
    }

    private static InboxDeliveryException permanent(String message) {
        return new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, message);
    }

}
