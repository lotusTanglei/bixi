package com.lotus.bixi.workflow.api.event;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed-schema codec for durable task-notification messages. */
public final class WorkflowTaskNotificationCodec {
    private static final int MAX_BYTES = 8 * 1024;
    private static final Set<String> COMMON_FIELDS = Set.of(
            "eventId", "schemaVersion", "sourceOwner", "targetOwner", "tenantScope",
            "processDefinitionId", "processInstanceId", "processKey", "taskId", "taskName",
            "businessKey", "commandId", "operationId", "occurredAt");
    private static final Set<String> FIELDS_V1 = fields("recipientUserId");
    private static final Set<String> FIELDS_V2 = fields("recipientUserIds", "recipientRoleIds");

    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public byte[] encode(WorkflowTaskNotification event) {
        if (event == null) throw new IllegalArgumentException("任务通知不能为空");
        ObjectNode value = json.createObjectNode();
        value.put("eventId", event.eventId());
        value.put("schemaVersion", event.schemaVersion());
        value.put("sourceOwner", event.sourceOwner());
        value.put("targetOwner", event.targetOwner());
        value.put("tenantScope", event.tenantScope());
        value.put("processDefinitionId", event.processDefinitionId());
        value.put("processInstanceId", event.processInstanceId());
        value.put("processKey", event.processKey());
        value.put("taskId", event.taskId());
        value.put("taskName", event.taskName());
        if (event.schemaVersion() == 1) {
            value.put("recipientUserId", event.recipientUserIds().get(0));
        } else {
            value.putPOJO("recipientUserIds", event.recipientUserIds());
            value.putPOJO("recipientRoleIds", event.recipientRoleIds());
        }
        if (event.businessKey() == null) value.putNull("businessKey");
        else value.put("businessKey", event.businessKey());
        value.put("commandId", event.commandId());
        value.put("operationId", event.operationId());
        value.put("occurredAt", event.occurredAt().toString());
        try {
            byte[] bytes = json.writeValueAsBytes(value);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("任务通知JSON超出大小限制");
            return bytes;
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("任务通知JSON编码失败");
        }
    }

    public WorkflowTaskNotification decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("任务通知JSON大小无效");
        }
        try {
            String source = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode parsed = json.readTree(source);
            validateUnicode(parsed);
            ObjectNode value = object(parsed);
            int schemaVersion = exactInt(value, "schemaVersion");
            requireFields(value, schemaVersion);
            return new WorkflowTaskNotification(
                    string(value, "eventId"), schemaVersion,
                    string(value, "sourceOwner"), string(value, "targetOwner"),
                    string(value, "tenantScope"), string(value, "processDefinitionId"),
                    string(value, "processInstanceId"), string(value, "processKey"),
                    string(value, "taskId"), string(value, "taskName"),
                    schemaVersion == 1 ? List.of(exactLong(value, "recipientUserId"))
                            : exactLongs(value, "recipientUserIds"),
                    schemaVersion == 1 ? List.of() : exactLongs(value, "recipientRoleIds"),
                    nullableString(value, "businessKey"),
                    string(value, "commandId"), string(value, "operationId"),
                    Instant.parse(string(value, "occurredAt")));
        } catch (CharacterCodingException | JsonProcessingException invalid) {
            throw new IllegalArgumentException("任务通知JSON无效");
        }
    }

    private static ObjectNode object(JsonNode node) {
        if (!(node instanceof ObjectNode value)) throw new IllegalArgumentException("任务通知JSON对象无效");
        return value;
    }

    private static void requireFields(ObjectNode value, int schemaVersion) {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        Set<String> expected = schemaVersion == 1 ? FIELDS_V1 : schemaVersion == 2 ? FIELDS_V2 : Set.of();
        if (!expected.equals(actual)) throw new IllegalArgumentException("任务通知JSON字段无效");
    }

    private static String string(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isTextual()) throw new IllegalArgumentException(name + "必须是字符串");
        return node.textValue();
    }

    private static String nullableString(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null) throw new IllegalArgumentException(name + "字段缺失");
        return node.isNull() ? null : string(value, name);
    }

    private static long exactLong(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isNumber()) throw new IllegalArgumentException(name + "必须是整数");
        try {
            return node.decimalValue().longValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(name + "必须是整数");
        }
    }

    private static List<Long> exactLongs(ObjectNode value, String name) {
        JsonNode node = value.get(name);
        if (node == null || !node.isArray()) throw new IllegalArgumentException(name + "必须是数组");
        List<Long> values = new ArrayList<>();
        node.forEach(item -> {
            if (!item.isNumber()) throw new IllegalArgumentException(name + "必须是整数数组");
            try {
                values.add(item.decimalValue().longValueExact());
            } catch (ArithmeticException invalid) {
                throw new IllegalArgumentException(name + "必须是整数数组");
            }
        });
        return List.copyOf(values);
    }

    private static Set<String> fields(String... recipients) {
        Set<String> fields = new HashSet<>(COMMON_FIELDS);
        fields.addAll(List.of(recipients));
        return Set.copyOf(fields);
    }

    private static int exactInt(ObjectNode value, String name) {
        long number = exactLong(value, name);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + "超出范围");
        }
        return (int) number;
    }

    private static void validateUnicode(JsonNode node) {
        if (node == null) return;
        if (node.isTextual()) requireValidUnicode(node.textValue());
        else if (node.isObject()) node.fields().forEachRemaining(entry -> {
            requireValidUnicode(entry.getKey());
            validateUnicode(entry.getValue());
        });
        else if (node.isArray()) node.forEach(WorkflowTaskNotificationCodec::validateUnicode);
    }

    private static void requireValidUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException("任务通知包含无效Unicode");
                }
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("任务通知包含无效Unicode");
            }
        }
    }
}
