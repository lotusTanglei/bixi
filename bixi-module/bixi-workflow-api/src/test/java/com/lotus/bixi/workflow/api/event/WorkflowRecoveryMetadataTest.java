package com.lotus.bixi.workflow.api.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRecoveryMetadataTest {

    @Test
    void extractsOnlyRecoveryCorrelationFromAnUnsupportedEventVersion() {
        String payload = """
                {"eventId":"00000000-0000-0000-0000-000000000041",
                 "type":"WORKFLOW_BUSINESS_TASK_RESULT","schemaVersion":2,
                 "processInstanceId":"process-41","businessTable":"demo_leave_request",
                 "businessId":41,"businessKey":"demo_leave:41:1","round":1,
                 "commandId":"00000000-0000-0000-0000-000000000011",
                 "correlationId":"00000000-0000-0000-0000-000000000011",
                 "payload":{"operationId":"00000000-0000-0000-0000-000000000042",
                            "compensationId":"00000000-0000-0000-0000-000000000043",
                            "bookingReference":"must-not-be-exposed"}}
                """;

        assertThat(WorkflowRecoveryMetadata.parse(payload)).hasValueSatisfying(metadata -> {
            assertThat(metadata.processInstanceId()).isEqualTo("process-41");
            assertThat(metadata.businessTable()).isEqualTo("demo_leave_request");
            assertThat(metadata.businessId()).isEqualTo(41L);
            assertThat(metadata.businessKey()).isEqualTo("demo_leave:41:1");
            assertThat(metadata.round()).isEqualTo(1);
            assertThat(metadata.requestId()).isEqualTo("00000000-0000-0000-0000-000000000011");
            assertThat(metadata.operationId()).isEqualTo("00000000-0000-0000-0000-000000000042");
            assertThat(metadata.compensationId()).isEqualTo("00000000-0000-0000-0000-000000000043");
            assertThat(metadata.toString()).doesNotContain("bookingReference", "must-not-be-exposed");
        });
    }

    @Test
    void returnsEmptyOnlyForInvalidJsonOrNonObjectPayloads() {
        assertThat(WorkflowRecoveryMetadata.parse("not-json")).isEmpty();
        assertThat(WorkflowRecoveryMetadata.parse("[]")).isEmpty();
        assertThat(WorkflowRecoveryMetadata.parse("{\"payload\":\"not-an-object\"}")).isPresent();
    }
}
