package com.lotus.bixi.workflow.api.event;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTaskNotificationCodecTest {

    private final WorkflowTaskNotificationCodec codec = new WorkflowTaskNotificationCodec();

    @Test
    void roundTripsTheFixedTaskNotificationContract() {
        WorkflowTaskNotification event = event();

        assertThat(codec.decode(codec.encode(event))).isEqualTo(event);
    }

    @Test
    void decodesTheLegacyAssignedRecipientContract() {
        String legacy = """
                {"eventId":"11111111-1111-1111-1111-111111111111","schemaVersion":1,
                "sourceOwner":"workflow","targetOwner":"upms","tenantScope":"42",
                "processDefinitionId":"demo_leave_approval:3:deployment-1","processInstanceId":"process-7",
                "processKey":"demo_leave_approval","taskId":"task-9","taskName":"请假审批",
                "recipientUserId":22,"businessKey":"leave:7:1",
                "commandId":"22222222-2222-2222-2222-222222222222",
                "operationId":"33333333-3333-3333-3333-333333333333",
                "occurredAt":"2026-09-24T08:00:00Z"}
                """;

        WorkflowTaskNotification decoded = codec.decode(legacy.getBytes(StandardCharsets.UTF_8));

        assertThat(decoded.schemaVersion()).isEqualTo(1);
        assertThat(decoded.recipientUserIds()).containsExactly(22L);
        assertThat(decoded.recipientRoleIds()).isEmpty();
    }

    @Test
    void rejectsUnknownFieldsAndInvalidRoutingIdentity() {
        String valid = new String(codec.encode(event()), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> codec.decode(valid.replaceFirst("\\}$", ",\"extra\":true}")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(valid.replace("\"targetOwner\":\"upms\"",
                        "\"targetOwner\":\"workflow\"").getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static WorkflowTaskNotification event() {
        return new WorkflowTaskNotification(
                "11111111-1111-1111-1111-111111111111", 2, "workflow", "upms", "42",
                "demo_leave_approval:3:deployment-1", "process-7", "demo_leave_approval",
                "task-9", "请假审批", List.of(22L, 33L), List.of(11L), "leave:7:1",
                "22222222-2222-2222-2222-222222222222",
                "33333333-3333-3333-3333-333333333333",
                Instant.parse("2026-09-24T08:00:00Z"));
    }
}
