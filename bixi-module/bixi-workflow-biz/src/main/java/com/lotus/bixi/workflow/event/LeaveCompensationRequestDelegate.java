package com.lotus.bixi.workflow.event;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Emits the durable compensation command when the automatic-task wait times out. */
@ConditionalOnWorkflowEnabled
public final class LeaveCompensationRequestDelegate implements JavaDelegate {
    private final WorkflowBusinessTaskEventPublisher publisher;

    public LeaveCompensationRequestDelegate(WorkflowBusinessTaskEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object rawOperation = execution.getVariable("businessOperationId");
        if (!(rawOperation instanceof String operationId) || operationId.isBlank()) {
            throw new IllegalStateException("businessOperationId is required");
        }
        long businessId = number(execution.getVariable("businessId"), "businessId");
        int round = Math.toIntExact(number(execution.getVariable("businessRound"), "businessRound"));
        String businessKey = text(execution.getVariable("businessKey"), "businessKey");
        String commandId = text(execution.getVariable("startRequestId"), "startRequestId");
        String requestHash = text(execution.getVariable("startRequestHash"), "startRequestHash");
        long actorId = number(execution.getVariable("startUserId"), "startUserId");
        String actorName = execution.getVariable("startUserName") == null
                ? Long.toString(actorId) : text(execution.getVariable("startUserName"), "startUserName");
        String compensationId = UUID.nameUUIDFromBytes((operationId + ":compensation:1")
                .getBytes(StandardCharsets.UTF_8)).toString();
        execution.setVariable("businessCompensationId", compensationId);
        publisher.publishCompensation(new WorkflowBusinessTaskEventPublisher.Context(
                execution.getProcessInstanceId(), "demo_leave_approval", businessId, businessKey, round,
                commandId, requestHash, commandId, null,
                new WorkflowActorSnapshot(actorId, actorName, "default", "upms", Instant.now()),
                execution.getId(), operationId, "compensateLeave", 1, Instant.now().plusSeconds(300)), compensationId);
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
