package com.lotus.bixi.workflow.api.event;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class WorkflowEventCodecTest {
    private final WorkflowEventCodec codec = new WorkflowEventCodec();
    private final String requestHash = "a".repeat(64);
    private final Instant now = Instant.parse("2026-09-21T12:34:56.123456789Z");
    private final WorkflowActorSnapshot actor = new WorkflowActorSnapshot(Long.MAX_VALUE,
            "申请人", "default", "upms", now.minusSeconds(30));

    @Test void eachFixedEventTypeRoundTripsWithoutMutablePayload() {
        var requested = event(WorkflowEventType.WORKFLOW_START_REQUESTED, null, 0,
                new WorkflowStartRequested("请假申请", 22L, requestHash));
        var started = event(WorkflowEventType.WORKFLOW_STARTED, "process-42", 1,
                new WorkflowStarted(requestHash));
        var rejected = event(WorkflowEventType.WORKFLOW_START_REJECTED, null, 1,
                new WorkflowStartRejected(requestHash, "APPROVER_UNAVAILABLE"));
        for (WorkflowEvent value : List.of(requested, started, rejected)) {
            assertThat(codec.decode(codec.encode(value))).isEqualTo(value);
        }
        for (WorkflowOutcome outcome : WorkflowOutcome.values()) {
            var completed = event(WorkflowEventType.WORKFLOW_COMPLETED, "process-42", 2,
                    new WorkflowCompleted(requestHash, outcome, now));
            assertThat(codec.decode(codec.encode(completed))).isEqualTo(completed);
            assertThat(new String(codec.encode(completed), StandardCharsets.UTF_8))
                    .contains("\"businessId\":" + Long.MAX_VALUE, "\"endedAt\":\"2026-09-21T12:34:56.123456789Z\"");
        }
    }

    @Test void rejectsInvalidAssociationIdentitySequenceAndOutcome() {
        assertThatThrownBy(() -> event(WorkflowEventType.WORKFLOW_STARTED, null, 1, new WorkflowStarted(requestHash)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event(WorkflowEventType.WORKFLOW_STARTED, "process-42", 2, new WorkflowStarted(requestHash)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event(WorkflowEventType.WORKFLOW_COMPLETED, "process-42", 1,
                new WorkflowCompleted(requestHash, WorkflowOutcome.APPROVED, now)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> event(WorkflowEventType.WORKFLOW_START_REQUESTED, "process-42", 0,
                new WorkflowStartRequested("请假申请", 22L, requestHash)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowStartRejected(requestHash, "A".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowStartRejected(requestHash, "UNKNOWN_CODE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowStartRequested("请假", 22L, "not-a-hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowActorSnapshot(1L, "user", "default", "workflow", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowActorSnapshot(1L, "\uD800", "default", "upms", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowEvent(UUID.randomUUID().toString(), WorkflowEventType.WORKFLOW_START_REQUESTED,
                1, "upms", "workflow", "default", null, "different_model", "demo_leave_request",
                Long.MAX_VALUE, "leave:42:1", 1, UUID.randomUUID().toString(), 0, now,
                UUID.randomUUID().toString(), null, actor, new WorkflowStartRequested("请假申请", 22L, requestHash)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void strictDecoderRejectsUnknownTypesSchemasFieldsAndDynamicMetadata() {
        String valid = new String(codec.encode(event(WorkflowEventType.WORKFLOW_START_REQUESTED, null, 0,
                new WorkflowStartRequested("请假申请", 22L, requestHash))), StandardCharsets.UTF_8);
        for (String invalid : List.of(
                valid.replace("WORKFLOW_START_REQUESTED", "com.example.Evil"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"__TypeId__\":\"evil\""),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
                valid.replace("\"title\":\"请假申请\"", "\"title\":\"请假申请\",\"callbackURL\":\"https://bad.invalid\""),
                valid.replace("\"businessId\":" + Long.MAX_VALUE, "\"businessId\":1e-1"),
                valid.replace("\"businessId\":" + Long.MAX_VALUE, "\"businessId\":\"42\""),
                valid.replace("\"businessTable\":\"demo_leave_request\"", "\"businessTable\":\"other\""),
                valid.replace("\"originatingService\":\"upms\"", "\"originatingService\":\"workflow\""),
                valid.replace("\"requestHash\":\"" + requestHash + "\"", "\"requestHash\":\"\\uD800\""),
                valid.replace("\"title\":\"请假申请\"", "\"\\uD800\":\"请假申请\",\"title\":\"请假申请\""),
                valid + " {}")) {
            assertThatThrownBy(() -> codec.decode(invalid.getBytes(StandardCharsets.UTF_8)))
                    .as("reject invalid JSON: %s", invalid).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void rejectedEventsOnlyAcceptPublishedErrorCodes() {
        String valid = new String(codec.encode(event(WorkflowEventType.WORKFLOW_START_REJECTED, null, 1,
                new WorkflowStartRejected(requestHash, "APPROVER_UNAVAILABLE"))), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> codec.decode(valid.replace("APPROVER_UNAVAILABLE", "UNKNOWN_CODE")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void decoderRejectsPayloadTypeMismatchAndMalformedUtf8() {
        String valid = new String(codec.encode(event(WorkflowEventType.WORKFLOW_START_REQUESTED, null, 0,
                new WorkflowStartRequested("请假申请", 22L, requestHash))), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> codec.decode(valid.replace("WORKFLOW_START_REQUESTED", "WORKFLOW_STARTED")
                .getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(new byte[] {'{', '"', (byte) 0xC3, '"', '}'}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void canonicalIntegerExponentsPreserveExactIdentityWithoutDoubleRounding() {
        var original = new WorkflowEvent(UUID.randomUUID().toString(), WorkflowEventType.WORKFLOW_START_REQUESTED,
                1, "upms", "workflow", "default", null, "demo_leave_approval", "demo_leave_request",
                9223372036854775800L, "leave:canonical:10", 10, UUID.randomUUID().toString(), 0, now,
                UUID.randomUUID().toString(), null, new WorkflowActorSnapshot(100L, "申请人", "default", "upms", now),
                new WorkflowStartRequested("请假", 200L, requestHash));
        String canonical = new String(codec.encode(original), StandardCharsets.UTF_8)
                .replace("\"businessId\":9223372036854775800", "\"businessId\":9.2233720368547758E+18")
                .replace("\"userId\":100", "\"userId\":1E+2")
                .replace("\"approverId\":200", "\"approverId\":2E+2")
                .replace("\"round\":10", "\"round\":1E+1")
                .replace("\"schemaVersion\":1", "\"schemaVersion\":1E+0")
                .replace("\"aggregateSequence\":0", "\"aggregateSequence\":0E+2");
        assertThat(codec.decode(canonical.getBytes(StandardCharsets.UTF_8))).isEqualTo(original);
        var completed = event(WorkflowEventType.WORKFLOW_COMPLETED, "process-42", 10,
                new WorkflowCompleted(requestHash, WorkflowOutcome.APPROVED, now));
        String completion = new String(codec.encode(completed), StandardCharsets.UTF_8)
                .replace("\"aggregateSequence\":10", "\"aggregateSequence\":1E+1");
        assertThat(codec.decode(completion.getBytes(StandardCharsets.UTF_8))).isEqualTo(completed);
    }

    @Test void numericConversionRejectsTrueFractionsAndExponentOverflowWithoutTruncation() {
        String valid = new String(codec.encode(event(WorkflowEventType.WORKFLOW_START_REQUESTED, null, 0,
                new WorkflowStartRequested("请假", 22L, requestHash))), StandardCharsets.UTF_8);
        for (String invalid : List.of(
                valid.replace("\"businessId\":" + Long.MAX_VALUE, "\"businessId\":9.223372036854775808E+18"),
                valid.replace("\"businessId\":" + Long.MAX_VALUE, "\"businessId\":9223372036854775807.1"),
                valid.replace("\"businessId\":" + Long.MAX_VALUE, "\"businessId\":1E+400"),
                valid.replace("\"userId\":" + Long.MAX_VALUE, "\"userId\":1E-1"),
                valid.replace("\"approverId\":22", "\"approverId\":2.21E+1"),
                valid.replace("\"round\":1", "\"round\":2.147483648E+9"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0000000000000000001"))) {
            assertThatThrownBy(() -> codec.decode(invalid.getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private WorkflowEvent event(WorkflowEventType type, String processId, long sequence, WorkflowPayload payload) {
        return new WorkflowEvent(UUID.randomUUID().toString(), type, 1,
                type == WorkflowEventType.WORKFLOW_START_REQUESTED ? "upms" : "workflow",
                type == WorkflowEventType.WORKFLOW_START_REQUESTED ? "workflow" : "upms",
                "default", processId, "demo_leave_approval", "demo_leave_request", Long.MAX_VALUE,
                "leave:42:1", 1, UUID.randomUUID().toString(), sequence, now, UUID.randomUUID().toString(),
                null, actor, payload);
    }
}
