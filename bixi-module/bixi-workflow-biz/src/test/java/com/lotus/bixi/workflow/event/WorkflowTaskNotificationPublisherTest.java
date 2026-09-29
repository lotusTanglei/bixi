package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotification;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotificationCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkflowTaskNotificationPublisherTest {

    @Test
    void persistsAnAssignedTaskNotificationInTheWorkflowOutbox() {
        WorkflowTaskNotificationPublisher.Outbox outbox = mock(WorkflowTaskNotificationPublisher.Outbox.class);
        WorkflowTaskNotificationCodec codec = new WorkflowTaskNotificationCodec();
        WorkflowTaskNotificationPublisher publisher = new WorkflowTaskNotificationPublisher(outbox, codec);

        publisher.record(new WorkflowTaskNotificationSink.Context(
                "demo_leave_approval:3:deployment-1", "process-7", "demo_leave_approval",
                "task-9", "请假审批", List.of(22L), List.of(), "42", "leave:7:1",
                "22222222-2222-2222-2222-222222222222",
                "33333333-3333-3333-3333-333333333333",
                Instant.parse("2026-09-24T08:00:00Z")));

        ArgumentCaptor<DurableMessage> captured = ArgumentCaptor.forClass(DurableMessage.class);
        verify(outbox).enqueue(captured.capture(), any(), isNull(), isNull());
        DurableMessage message = captured.getValue();
        assertThat(message.sourceOwner()).isEqualTo("workflow");
        assertThat(message.targetOwner()).isEqualTo("upms");
        assertThat(message.type()).isEqualTo(WorkflowTaskNotification.TYPE);
        WorkflowTaskNotification event = codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
        assertThat(event.taskId()).isEqualTo("task-9");
        assertThat(message.schemaVersion()).isEqualTo(2);
        assertThat(event.recipientUserIds()).containsExactly(22L);
        assertThat(event.recipientRoleIds()).isEmpty();
        assertThat(event.operationId()).isEqualTo("33333333-3333-3333-3333-333333333333");
    }
}
