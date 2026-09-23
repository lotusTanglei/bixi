package com.lotus.bixi.workflow.api.event;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lotus.bixi.workflow.api.json.WorkflowJsonVariables;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Fixed-schema JSON codec; no Jackson default typing or application HTTP mapper. */
public final class WorkflowEventCodec {
    private static final int MAX_BYTES = 16 * 1024;
    private static final Set<String> ENVELOPE_FIELDS = Set.of("eventId", "type", "schemaVersion",
            "sourceOwner", "targetOwner", "tenantScope", "processInstanceId", "processKey",
            "businessTable", "businessId", "businessKey", "round", "commandId", "aggregateSequence",
            "occurredAt", "correlationId", "causationId", "actor", "payload");
    private static final Set<String> ACTOR_FIELDS = Set.of("userId", "username", "tenantScope",
            "originatingService", "authorizedAt");
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public byte[] encode(WorkflowEvent event) {
        if (event == null) throw new IllegalArgumentException("事件不能为空");
        ObjectNode root = json.createObjectNode();
        root.put("eventId", event.eventId());
        root.put("type", event.type().name());
        root.put("schemaVersion", event.schemaVersion());
        root.put("sourceOwner", event.sourceOwner());
        root.put("targetOwner", event.targetOwner());
        root.put("tenantScope", event.tenantScope());
        if (event.processInstanceId() == null) root.putNull("processInstanceId");
        else root.put("processInstanceId", event.processInstanceId());
        root.put("processKey", event.processKey());
        root.put("businessTable", event.businessTable());
        root.put("businessId", event.businessId());
        root.put("businessKey", event.businessKey());
        root.put("round", event.round());
        root.put("commandId", event.commandId());
        root.put("aggregateSequence", event.aggregateSequence());
        root.put("occurredAt", event.occurredAt().toString());
        root.put("correlationId", event.correlationId());
        if (event.causationId() == null) root.putNull("causationId");
        else root.put("causationId", event.causationId());
        var actor = event.actor();
        ObjectNode actorNode = root.putObject("actor");
        actorNode.put("userId", actor.userId());
        actorNode.put("username", actor.username());
        actorNode.put("tenantScope", actor.tenantScope());
        actorNode.put("originatingService", actor.originatingService());
        actorNode.put("authorizedAt", actor.authorizedAt().toString());
        ObjectNode payload = root.putObject("payload");
        payload.put("requestHash", event.payload().requestHash());
        switch (event.type()) {
            case WORKFLOW_START_REQUESTED -> {
                var value = (WorkflowStartRequested) event.payload();
                payload.put("title", value.title());
                payload.put("approverId", value.approverId());
            }
            case WORKFLOW_STARTED -> { }
            case WORKFLOW_START_REJECTED -> payload.put("errorCode", ((WorkflowStartRejected) event.payload()).errorCode());
            case WORKFLOW_COMPLETED -> {
                var value = (WorkflowCompleted) event.payload();
                payload.put("outcome", value.outcome().name());
                payload.put("endedAt", value.endedAt().toString());
            }
            case WORKFLOW_BUSINESS_TASK_REQUESTED -> {
                var value = (WorkflowBusinessTaskRequested) event.payload();
                payload.put("operationId", value.operationId());
                payload.put("executionId", value.executionId());
                payload.put("activityId", value.activityId());
                payload.put("activityOccurrence", value.activityOccurrence());
                payload.put("deadline", value.deadline().toString());
            }
            case WORKFLOW_BUSINESS_TASK_RESULT -> {
                var value = (WorkflowBusinessTaskResult) event.payload();
                payload.put("operationId", value.operationId());
                payload.put("success", value.success());
                if (value.bookingReference() == null) payload.putNull("bookingReference");
                else payload.put("bookingReference", value.bookingReference());
                if (value.errorCode() == null) payload.putNull("errorCode");
                else payload.put("errorCode", value.errorCode());
                payload.put("completedAt", value.completedAt().toString());
            }
            case WORKFLOW_COMPENSATION_REQUESTED -> {
                var value = (WorkflowCompensationRequested) event.payload();
                payload.put("operationId", value.operationId());
                payload.put("compensationId", value.compensationId());
            }
            case WORKFLOW_COMPENSATION_RESULT -> {
                var value = (WorkflowCompensationResult) event.payload();
                payload.put("operationId", value.operationId());
                payload.put("compensationId", value.compensationId());
                payload.put("success", value.success());
                if (value.errorCode() == null) payload.putNull("errorCode");
                else payload.put("errorCode", value.errorCode());
                payload.put("completedAt", value.completedAt().toString());
            }
        }
        try {
            byte[] bytes = json.writeValueAsBytes(root);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("事件JSON超出大小限制");
            return bytes;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("事件JSON编码失败", error);
        }
    }

    public WorkflowEvent decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("事件JSON大小无效");
        }
        try {
            String source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root = json.readTree(source);
            validateStrings(root);
            ObjectNode envelope = object(root, ENVELOPE_FIELDS);
            WorkflowEventType type = WorkflowEventType.valueOf(string(envelope, "type"));
            var actorNode = object(envelope.get("actor"), ACTOR_FIELDS);
            WorkflowActorSnapshot actor = new WorkflowActorSnapshot(integer(actorNode, "userId"),
                    string(actorNode, "username"), string(actorNode, "tenantScope"),
                    string(actorNode, "originatingService"), instant(actorNode, "authorizedAt"));
            WorkflowPayload payload = payload(type, envelope.get("payload"));
            return new WorkflowEvent(string(envelope, "eventId"), type, exactInt(envelope, "schemaVersion"),
                    string(envelope, "sourceOwner"), string(envelope, "targetOwner"),
                    string(envelope, "tenantScope"), nullableString(envelope, "processInstanceId"),
                    string(envelope, "processKey"), string(envelope, "businessTable"),
                    integer(envelope, "businessId"), string(envelope, "businessKey"),
                    exactInt(envelope, "round"), string(envelope, "commandId"),
                    integer(envelope, "aggregateSequence"), instant(envelope, "occurredAt"),
                    string(envelope, "correlationId"), nullableString(envelope, "causationId"), actor, payload);
        } catch (CharacterCodingException | JsonProcessingException error) {
            throw new IllegalArgumentException("无效的事件JSON", error);
        }
    }

    private WorkflowPayload payload(WorkflowEventType type, JsonNode node) {
        return switch (type) {
            case WORKFLOW_START_REQUESTED -> {
                ObjectNode fields = object(node, Set.of("requestHash", "title", "approverId"));
                yield new WorkflowStartRequested(string(fields, "title"), integer(fields, "approverId"),
                        string(fields, "requestHash"));
            }
            case WORKFLOW_STARTED -> {
                ObjectNode fields = object(node, Set.of("requestHash"));
                yield new WorkflowStarted(string(fields, "requestHash"));
            }
            case WORKFLOW_START_REJECTED -> {
                ObjectNode fields = object(node, Set.of("requestHash", "errorCode"));
                yield new WorkflowStartRejected(string(fields, "requestHash"), string(fields, "errorCode"));
            }
            case WORKFLOW_COMPLETED -> {
                ObjectNode fields = object(node, Set.of("requestHash", "outcome", "endedAt"));
                yield new WorkflowCompleted(string(fields, "requestHash"),
                        WorkflowOutcome.valueOf(string(fields, "outcome")), instant(fields, "endedAt"));
            }
            case WORKFLOW_BUSINESS_TASK_REQUESTED -> {
                ObjectNode fields = object(node, Set.of("requestHash", "operationId", "executionId",
                        "activityId", "activityOccurrence", "deadline"));
                yield new WorkflowBusinessTaskRequested(string(fields, "requestHash"), string(fields, "operationId"),
                        string(fields, "executionId"), string(fields, "activityId"),
                        exactInt(fields, "activityOccurrence"), instant(fields, "deadline"));
            }
            case WORKFLOW_BUSINESS_TASK_RESULT -> {
                ObjectNode fields = object(node, Set.of("requestHash", "operationId", "success",
                        "bookingReference", "errorCode", "completedAt"));
                yield new WorkflowBusinessTaskResult(string(fields, "requestHash"), string(fields, "operationId"),
                        bool(fields, "success"), nullableString(fields, "bookingReference"),
                        nullableString(fields, "errorCode"), instant(fields, "completedAt"));
            }
            case WORKFLOW_COMPENSATION_REQUESTED -> {
                ObjectNode fields = object(node, Set.of("requestHash", "operationId", "compensationId"));
                yield new WorkflowCompensationRequested(string(fields, "requestHash"), string(fields, "operationId"),
                        string(fields, "compensationId"));
            }
            case WORKFLOW_COMPENSATION_RESULT -> {
                ObjectNode fields = object(node, Set.of("requestHash", "operationId", "compensationId",
                        "success", "errorCode", "completedAt"));
                yield new WorkflowCompensationResult(string(fields, "requestHash"), string(fields, "operationId"),
                        string(fields, "compensationId"), bool(fields, "success"),
                        nullableString(fields, "errorCode"), instant(fields, "completedAt"));
            }
        };
    }

    private static ObjectNode object(JsonNode node, Set<String> expected) {
        if (!(node instanceof ObjectNode value)) throw new IllegalArgumentException("事件JSON对象无效");
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!expected.equals(actual)) throw new IllegalArgumentException("事件JSON字段无效");
        return value;
    }

    private static String string(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isTextual()) throw new IllegalArgumentException(name + "必须是字符串");
        return node.textValue();
    }

    private static String nullableString(ObjectNode value, String name) {
        if (value.get(name).isNull()) return null;
        return string(value, name);
    }

    private static boolean bool(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isBoolean()) throw new IllegalArgumentException(name + "必须是布尔值");
        return node.booleanValue();
    }

    private static long integer(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isNumber()) throw new IllegalArgumentException(name + "必须是整数");
        try {
            // DurableMessage canonicalization may render an exact integer as an exponent.
            // Keep decimal precision and reject both fractions and overflow without rounding.
            return node.decimalValue().longValueExact();
        } catch (ArithmeticException invalidInteger) {
            throw new IllegalArgumentException(name + "必须是Long范围内的整数", invalidInteger);
        }
    }

    private static int exactInt(ObjectNode value, String name) {
        try {
            return Math.toIntExact(integer(value, name));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + "超出Integer范围", overflow);
        }
    }

    private static Instant instant(ObjectNode value, String name) {
        return Instant.parse(string(value, name));
    }

    private static void validateStrings(JsonNode node) {
        if (node == null) throw new IllegalArgumentException("事件JSON不能为空");
        if (node.isTextual()) WorkflowJsonVariables.requireUnicodeScalars(node.textValue());
        else if (node.isObject()) node.fields().forEachRemaining(field -> {
            WorkflowJsonVariables.requireUnicodeScalars(field.getKey());
            validateStrings(field.getValue());
        });
        else if (node.isArray()) node.forEach(WorkflowEventCodec::validateStrings);
    }
}
