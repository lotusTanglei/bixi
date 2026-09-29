package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotification;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotificationCodec;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Persists task notifications in the workflow owner's transaction. */
public final class WorkflowTaskNotificationPublisher implements WorkflowTaskNotificationSink {
    private final Outbox outbox;
    private final WorkflowTaskNotificationCodec codec;

    public WorkflowTaskNotificationPublisher(@Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("workflowTaskNotificationCodec") WorkflowTaskNotificationCodec codec) {
        this(outbox::enqueue, codec);
    }

    WorkflowTaskNotificationPublisher(Outbox outbox, WorkflowTaskNotificationCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    @Override
    public void record(Context context) {
        String eventId = namedId(context.operationId() + ":requested");
        WorkflowTaskNotification event = new WorkflowTaskNotification(
                eventId, 2, "workflow", "upms", context.tenantScope(), context.processDefinitionId(),
                context.processInstanceId(), context.processKey(), context.taskId(), context.taskName(),
                context.recipientUserIds(), context.recipientRoleIds(), context.businessKey(),
                context.commandId(), context.operationId(),
                context.occurredAt());
        String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
        outbox.enqueue(DurableMessage.create("workflow", "upms", eventId,
                        WorkflowTaskNotification.TYPE, 2, payload),
                "task-notification:" + eventId, null, null);
    }

    static String namedId(String identity) {
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    @FunctionalInterface
    interface Outbox {
        void enqueue(DurableMessage message, String dedupKey, String aggregateKey, Long aggregateSequence);
    }
}
