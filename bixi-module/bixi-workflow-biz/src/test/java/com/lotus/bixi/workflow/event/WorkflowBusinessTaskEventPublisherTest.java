package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowBusinessTaskRequested;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkflowBusinessTaskEventPublisherTest {
    @Test
    void publishesVersionedBusinessTaskRequestToUpmsOutbox() {
        WorkflowBusinessTaskEventPublisher.Outbox outbox = mock(WorkflowBusinessTaskEventPublisher.Outbox.class);
        WorkflowEventCodec codec = new WorkflowEventCodec();
        WorkflowBusinessTaskEventPublisher publisher = new WorkflowBusinessTaskEventPublisher(outbox, codec);
        String operationId = UUID.randomUUID().toString();
        String commandId = UUID.randomUUID().toString();
        String hash = "a".repeat(64);

        publisher.publish(new WorkflowBusinessTaskEventPublisher.Context(
                "process-7", "demo_leave_approval", 7L, "leave:7:1", 1, commandId,
                hash, UUID.randomUUID().toString(), null,
                new WorkflowActorSnapshot(42L, "applicant", "default", "upms", Instant.now()),
                "execution-7", operationId, "bookLeave", 1, Instant.parse("2026-09-22T00:05:00Z")));

        ArgumentCaptor<DurableMessage> captured = ArgumentCaptor.forClass(DurableMessage.class);
        verify(outbox).enqueue(captured.capture(), any(), any(), any());
        DurableMessage message = captured.getValue();
        assertThat(message.sourceOwner()).isEqualTo("workflow");
        assertThat(message.targetOwner()).isEqualTo("upms");
        assertThat(message.type()).isEqualTo(WorkflowEventType.WORKFLOW_BUSINESS_TASK_REQUESTED.name());
        WorkflowEvent event = codec.decode(message.payloadJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(event.payload()).isInstanceOf(WorkflowBusinessTaskRequested.class);
        WorkflowBusinessTaskRequested payload = (WorkflowBusinessTaskRequested) event.payload();
        assertThat(payload.operationId()).isEqualTo(operationId);
        assertThat(payload.executionId()).isEqualTo("execution-7");
        assertThat(event.aggregateSequence()).isEqualTo(2);
    }
}
