package com.lotus.bixi.workflow.command;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.json.WorkflowJsonVariables;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Version 1: sorted objects, ordered arrays, exact decimal numbers, UTF-8 SHA-256. */
@Component
@ConditionalOnWorkflowEnabled
public class WorkflowRequestHasher {
    public static final int HASH_VERSION = 1;
    public record Actor(String tenantScope, Long userId) { }
    private final ObjectMapper json = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public JsonNode parse(String value) {
        try {
            JsonNode parsed = json.readTree(value);
            if (parsed == null) throw new IllegalArgumentException("JSON内容不能为空");
            return normalize(parsed);
        } catch (java.io.IOException | ArithmeticException e) {
            throw new IllegalArgumentException("无效的JSON内容", e);
        }
    }

    /** Only JSON values are accepted, including values supplied through the local adapter. */
    public JsonNode normalize(Object value) {
        if (value == null || value instanceof NullNode) return NullNode.instance;
        if (value instanceof JsonNode node) {
            if (node.isObject()) {
                var fields = new TreeMap<String, Object>();
                node.fields().forEachRemaining(field -> fields.put(field.getKey(), field.getValue()));
                return normalize(fields);
            }
            if (node.isArray()) {
                var array = JsonNodeFactory.instance.arrayNode();
                node.forEach(element -> array.add(normalize(element)));
                return array;
            }
            if (node.isTextual()) return TextNode.valueOf(WorkflowJsonVariables.requireUnicodeScalars(node.textValue()));
            if (node.isBoolean()) return BooleanNode.valueOf(node.booleanValue());
            if (node.isNumber()) return normalize(node.numberValue());
            throw new IllegalArgumentException("只允许JSON值");
        }
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("JSON对象键必须是字符串");
                sorted.put(WorkflowJsonVariables.requireUnicodeScalars(text), item);
            });
            var object = JsonNodeFactory.instance.objectNode();
            sorted.forEach((key, item) -> object.set(key, normalize(item)));
            return object;
        }
        if (value instanceof List<?> list) {
            var array = JsonNodeFactory.instance.arrayNode();
            list.forEach(item -> array.add(normalize(item)));
            return array;
        }
        if (value instanceof String text) return TextNode.valueOf(WorkflowJsonVariables.requireUnicodeScalars(text));
        if (value instanceof Boolean bool) return BooleanNode.valueOf(bool);
        if (value instanceof Number number) {
            if (!(number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
                    || number instanceof Float || number instanceof Double || number instanceof BigInteger || number instanceof BigDecimal)
                    || (number instanceof Double d && !Double.isFinite(d))
                    || (number instanceof Float f && !Float.isFinite(f))) {
                throw new IllegalArgumentException("JSON数字必须是有限十进制数");
            }
            BigDecimal decimal = new BigDecimal(number.toString()).stripTrailingZeros();
            if (decimal.precision() > 1000 || Math.abs((long) decimal.scale()) > 1000) {
                throw new IllegalArgumentException("JSON数字超出支持范围");
            }
            return DecimalNode.valueOf(decimal);
        }
        throw new IllegalArgumentException("只允许JSON值");
    }

    public String canonical(JsonNode value) {
        JsonNode normalized = normalize(value);
        if (normalized.isNumber()) return normalized.decimalValue().toPlainString();
        if (normalized.isArray()) {
            List<String> items = new ArrayList<>();
            normalized.forEach(item -> items.add(canonical(item)));
            return "[" + String.join(",", items) + "]";
        }
        if (normalized.isObject()) {
            List<String> fields = new ArrayList<>();
            normalized.fields().forEachRemaining(field -> fields.add(TextNode.valueOf(field.getKey())
                    + ":" + canonical(field.getValue())));
            return "{" + String.join(",", fields) + "}";
        }
        return normalized.toString();
    }

    public String hash(String operation, String resourceId, String sourceOwner, Actor actor, JsonNode payload) {
        Map<String, Object> content = new HashMap<>();
        content.put("hashVersion", HASH_VERSION);
        content.put("operation", operation);
        content.put("resourceId", resourceId);
        content.put("sourceOwner", sourceOwner);
        content.put("tenantScope", actor.tenantScope());
        content.put("actorId", actor.userId());
        content.put("payload", payload);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical(normalize(content)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Parse the exact canonical representation for execution, avoiding input-dependent Java number types. */
    public Map<String, Object> variables(JsonNode node) {
        try {
            return json.readValue(canonical(node), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("无效的流程变量", e);
        }
    }
}
