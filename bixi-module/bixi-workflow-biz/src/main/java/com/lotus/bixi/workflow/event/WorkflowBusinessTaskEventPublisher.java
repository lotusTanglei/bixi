package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowCompensationRequested;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Creates the fixed workflow-to-UPMS automatic-task request in the local outbox transaction. */
public final class WorkflowBusinessTaskEventPublisher {
    private static final String PROCESS_KEY = "demo_leave_approval";
    private static final String BUSINESS_TABLE = "demo_leave_request";

    private final Outbox outbox;
    private final WorkflowEventCodec codec;

    public WorkflowBusinessTaskEventPublisher(@Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        this(outbox::enqueue, codec);
    }

    WorkflowBusinessTaskEventPublisher(Outbox outbox, WorkflowEventCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    public void publish(Context context) {
        WorkflowBusinessTaskRequested payload = new WorkflowBusinessTaskRequested(context.requestHash(),
                context.operationId(), context.executionId(), context.activityId(), context.activityOccurrence(),
                context.deadline());
        WorkflowEvent event = new WorkflowEvent(eventId(context.operationId(), "requested"),
                WorkflowEventType.WORKFLOW_BUSINESS_TASK_REQUESTED, 1, "workflow", "upms", "default",
                context.processInstanceId(), PROCESS_KEY, BUSINESS_TABLE, context.businessId(), context.businessKey(),
                context.round(), context.commandId(), 2, Instant.now(), context.correlationId(),
                context.causationId(), context.actor(), payload);
        String json = new String(codec.encode(event), StandardCharsets.UTF_8);
        outbox.enqueue(DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(), 1, json),
                "business-task:" + context.operationId() + ":requested", context.businessKey(), 2L);
    }

    public void publishCompensation(Context context, String compensationId) {
        WorkflowCompensationRequested payload = new WorkflowCompensationRequested(context.requestHash(),
                context.operationId(), compensationId);
        WorkflowEvent event = new WorkflowEvent(eventId(compensationId, "requested"),
                WorkflowEventType.WORKFLOW_COMPENSATION_REQUESTED, 1, "workflow", "upms", "default",
                context.processInstanceId(), PROCESS_KEY, BUSINESS_TABLE, context.businessId(), context.businessKey(),
                context.round(), context.commandId(), 4, Instant.now(), context.correlationId(),
                context.causationId(), context.actor(), payload);
        String json = new String(codec.encode(event), StandardCharsets.UTF_8);
        outbox.enqueue(DurableMessage.create("workflow", "upms", event.eventId(), event.type().name(), 1, json),
                "compensation:" + compensationId + ":requested", context.businessKey(), 4L);
    }

    private static String eventId(String operationId, String suffix) {
        return UUID.nameUUIDFromBytes((operationId + ":" + suffix).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public record Context(String processInstanceId, String processKey, long businessId, String businessKey,
                          int round, String commandId, String requestHash, String correlationId,
                          String causationId, WorkflowActorSnapshot actor, String executionId,
                          String operationId, String activityId, int activityOccurrence, Instant deadline) {
    }

    @FunctionalInterface
    interface Outbox {
        void enqueue(DurableMessage message, String dedupKey, String aggregateKey, Long aggregateSequence);
    }
}
