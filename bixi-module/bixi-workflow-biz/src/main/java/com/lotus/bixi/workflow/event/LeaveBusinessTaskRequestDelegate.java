package com.lotus.bixi.workflow.event;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Flowable service-task delegate that emits the durable leave booking request. */
@ConditionalOnWorkflowEnabled
public final class LeaveBusinessTaskRequestDelegate implements JavaDelegate {
    private final WorkflowBusinessTaskEventPublisher publisher;

    public LeaveBusinessTaskRequestDelegate(WorkflowBusinessTaskEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void execute(DelegateExecution execution) {
        long businessId = number(execution.getVariable("businessId"), "businessId");
        int round = Math.toIntExact(number(execution.getVariable("businessRound"), "businessRound"));
        String businessKey = text(execution.getVariable("businessKey"), "businessKey");
        String commandId = text(execution.getVariable("startRequestId"), "startRequestId");
        String requestHash = text(execution.getVariable("startRequestHash"), "startRequestHash");
        long actorId = number(execution.getVariable("startUserId"), "startUserId");
        String actorName = execution.getVariable("startUserName") == null
                ? Long.toString(actorId) : text(execution.getVariable("startUserName"), "startUserName");
        String activityId = text(execution.getCurrentActivityId(), "activityId");
        String operationId = UUID.nameUUIDFromBytes((execution.getProcessInstanceId() + ":" + activityId + ":1")
                .getBytes(StandardCharsets.UTF_8)).toString();
        execution.setVariable("businessOperationId", operationId);
        publisher.publish(new WorkflowBusinessTaskEventPublisher.Context(
                execution.getProcessInstanceId(), "demo_leave_approval", businessId, businessKey, round,
                commandId, requestHash, commandId, null,
                new com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot(actorId, actorName, "default", "upms", Instant.now()),
                execution.getId(), operationId, activityId, 1, Instant.now().plusSeconds(300)));
    }

    private static String text(Object value, String name) {
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalStateException(name + " is required");
        return text;
    }

    private static long number(Object value, String name) {
        if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        if (value instanceof String text && text.matches("[1-9][0-9]{0,18}")) {
            try { return Long.parseLong(text); }
            catch (NumberFormatException ignored) { }
        }
        throw new IllegalStateException(name + " is required");
    }
}
