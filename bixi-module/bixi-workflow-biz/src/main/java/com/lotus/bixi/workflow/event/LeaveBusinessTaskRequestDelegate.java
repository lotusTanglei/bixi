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
    static final String ACTIVITY_OCCURRENCE_VARIABLE = "businessTaskActivityOccurrence";
    static final String ACTIVITY_ID_VARIABLE = "businessTaskActivityId";

    private final WorkflowBusinessTaskEventPublisher publisher;
    private final WorkflowBusinessTaskStore tasks;

    public LeaveBusinessTaskRequestDelegate(WorkflowBusinessTaskEventPublisher publisher) {
        this(publisher, null);
    }

    public LeaveBusinessTaskRequestDelegate(WorkflowBusinessTaskEventPublisher publisher,
            WorkflowBusinessTaskStore tasks) {
        this.publisher = publisher;
        this.tasks = tasks;
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
        String tenantScope = text(execution.getVariable("tenantScope"), "tenantScope");
        String activityId = text(execution.getCurrentActivityId(), "activityId");
        int activityOccurrence = nextOccurrence(execution.getVariable(ACTIVITY_OCCURRENCE_VARIABLE));
        String operationId = UUID.nameUUIDFromBytes((execution.getProcessInstanceId() + ":" + activityId + ":"
                + activityOccurrence)
                .getBytes(StandardCharsets.UTF_8)).toString();
        execution.setVariable("businessOperationId", operationId);
        execution.setVariable(ACTIVITY_ID_VARIABLE, activityId);
        execution.setVariable(ACTIVITY_OCCURRENCE_VARIABLE, activityOccurrence);
        WorkflowBusinessTaskEventPublisher.Context context = new WorkflowBusinessTaskEventPublisher.Context(
                execution.getProcessInstanceId(), "demo_leave_approval", businessId, businessKey, round,
                commandId, requestHash, commandId, null,
                new com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot(actorId, actorName, tenantScope, "upms", Instant.now()),
                execution.getId(), operationId, activityId, activityOccurrence, Instant.now().plusSeconds(300));
        if (tasks != null) tasks.createWaiting(context);
        publisher.publish(context);
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

    private static int nextOccurrence(Object value) {
        long previous;
        if (value == null) {
            previous = 0;
        }
        else if (value instanceof Number number) {
            previous = number.longValue();
        }
        else if (value instanceof String text && text.matches("[0-9]{1,9}")) {
            previous = Long.parseLong(text);
        }
        else {
            throw new IllegalStateException("businessTaskActivityOccurrence is invalid");
        }
        if (previous < 0 || previous >= Integer.MAX_VALUE) {
            throw new IllegalStateException("businessTaskActivityOccurrence is invalid");
        }
        return Math.toIntExact(previous + 1);
    }
}
