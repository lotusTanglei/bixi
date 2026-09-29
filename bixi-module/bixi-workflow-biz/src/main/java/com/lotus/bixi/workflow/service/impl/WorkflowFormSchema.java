package com.lotus.bixi.workflow.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Compiles the non-executable VForm subset accepted by the workflow runtime. */
@Component
@ConditionalOnWorkflowEnabled
public final class WorkflowFormSchema {

    private static final int MAX_SCHEMA_BYTES = 1024 * 1024;
    private static final int MAX_DATA_BYTES = 256 * 1024;
    private static final int MAX_FIELDS = 256;
    private static final int MAX_DEPTH = 16;
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Set<String> CONTAINER_TYPES = Set.of(
            "grid", "table", "tab", "section", "card", "sub-form", "divider");
    private static final Map<String, ValueKind> FIELD_TYPES = Map.ofEntries(
            Map.entry("input", ValueKind.STRING),
            Map.entry("textarea", ValueKind.STRING),
            Map.entry("date", ValueKind.STRING),
            Map.entry("time", ValueKind.STRING),
            Map.entry("color", ValueKind.STRING),
            Map.entry("radio", ValueKind.SCALAR),
            Map.entry("select", ValueKind.SCALAR),
            Map.entry("number", ValueKind.NUMBER),
            Map.entry("slider", ValueKind.NUMBER),
            Map.entry("rate", ValueKind.NUMBER),
            Map.entry("switch", ValueKind.BOOLEAN),
            Map.entry("checkbox", ValueKind.ARRAY),
            Map.entry("cascader", ValueKind.ARRAY));
    private static final Set<String> RESERVED_FIELDS = Set.of("__proto__", "prototype", "constructor");

    private final ObjectMapper objectMapper;

    public WorkflowFormSchema(ObjectMapper objectMapper) {
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Compiled compile(String schemaJson) {
        ObjectNode root = parseObject(schemaJson, MAX_SCHEMA_BYTES, "表单 schema");
        JsonNode widgets = root.get("widgetList");
        if (widgets == null || !widgets.isArray()) {
            throw invalid("表单 schema 必须包含 widgetList 数组");
        }
        LinkedHashMap<String, FieldRule> fields = new LinkedHashMap<>();
        inspect(root, fields, 0);
        return new Compiled(root.deepCopy(), Collections.unmodifiableMap(fields));
    }

    public ObjectNode validate(Compiled schema, String dataJson) {
        if (schema == null) {
            throw invalid("表单 schema 不能为空");
        }
        ObjectNode data = parseObject(dataJson, MAX_DATA_BYTES, "表单数据");
        Iterator<String> submitted = data.fieldNames();
        while (submitted.hasNext()) {
            String name = submitted.next();
            if (!schema.fields().containsKey(name)) {
                throw invalid("未知字段: " + name);
            }
        }
        for (FieldRule field : schema.fields().values()) {
            validateValue(field, data.get(field.name()));
        }
        return data.deepCopy();
    }

    private void inspect(JsonNode node, Map<String, FieldRule> fields, int depth) {
        if (depth > MAX_DEPTH) {
            throw invalid("表单 schema 嵌套层级不能超过 " + MAX_DEPTH);
        }
        if (node.isArray()) {
            node.forEach(child -> inspect(child, fields, depth + 1));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        rejectExecutableContent(node);
        JsonNode typeNode = node.get("type");
        JsonNode options = node.get("options");
        if (typeNode != null && typeNode.isTextual() && options != null && options.isObject()) {
            String type = typeNode.textValue();
            String name = text(options.get("name"));
            if (name != null) {
                addField(type, name, options, fields);
            } else if (!CONTAINER_TYPES.contains(type)) {
                throw invalid("表单组件缺少字段名: " + type);
            }
        }
        node.elements().forEachRemaining(child -> inspect(child, fields, depth + 1));
    }

    private void addField(String type, String name, JsonNode options, Map<String, FieldRule> fields) {
        if (!FIELD_NAME.matcher(name).matches() || RESERVED_FIELDS.contains(name)) {
            throw invalid("字段名不合法: " + name);
        }
        ValueKind kind = FIELD_TYPES.get(type);
        if (kind == null) {
            throw invalid("不支持的表单组件: " + type);
        }
        if ("select".equals(type) && options.path("multiple").asBoolean(false)) {
            kind = ValueKind.ARRAY;
        }
        Integer minLength = integer(options.get("minLength"), "minLength", name);
        Integer maxLength = integer(options.get("maxLength"), "maxLength", name);
        if (minLength != null && minLength < 0 || maxLength != null && maxLength < 0
                || minLength != null && maxLength != null && minLength > maxLength) {
            throw invalid("字段长度约束不合法: " + name);
        }
        Double min = decimal(options.get("min"), "min", name);
        Double max = decimal(options.get("max"), "max", name);
        if (min != null && max != null && min > max) {
            throw invalid("字段数值约束不合法: " + name);
        }
        String label = text(options.get("label"));
        FieldRule rule = new FieldRule(name, label == null ? name : label, type, kind,
                options.path("required").asBoolean(false),
                minLength, maxLength, min, max);
        if (fields.putIfAbsent(name, rule) != null) {
            throw invalid("表单字段重复: " + name);
        }
        if (fields.size() > MAX_FIELDS) {
            throw invalid("表单字段不能超过 " + MAX_FIELDS);
        }
    }

    private void rejectExecutableContent(JsonNode object) {
        object.fields().forEachRemaining(entry -> {
            String key = entry.getKey().toLowerCase(java.util.Locale.ROOT);
            JsonNode value = entry.getValue();
            if ((key.contains("script") || key.startsWith("on")) && !isEmpty(value)) {
                throw invalid("表单 schema 不允许可执行配置: " + entry.getKey());
            }
            if (value.isTextual()) {
                String text = value.textValue().toLowerCase(java.util.Locale.ROOT);
                if (text.contains("javascript:") || text.contains("=>") || text.contains("function(")) {
                    throw invalid("表单 schema 不允许可执行内容: " + entry.getKey());
                }
            }
        });
    }

    private void validateValue(FieldRule field, JsonNode value) {
        if (value == null || value.isNull() || field.kind() == ValueKind.STRING && value.isTextual() && value.textValue().isBlank()) {
            if (field.required()) {
                throw invalid("字段 " + field.name() + " 为必填项");
            }
            return;
        }
        boolean validType = switch (field.kind()) {
            case STRING -> value.isTextual();
            case NUMBER -> value.isNumber();
            case BOOLEAN -> value.isBoolean();
            case ARRAY -> value.isArray();
            case SCALAR -> value.isTextual() || value.isNumber() || value.isBoolean();
        };
        if (!validType) {
            String expected = switch (field.kind()) {
                case STRING -> "字符串";
                case NUMBER -> "数字";
                case BOOLEAN -> "布尔值";
                case ARRAY -> "数组";
                case SCALAR -> "标量";
            };
            throw invalid("字段 " + field.name() + " 必须是" + expected);
        }
        if (value.isTextual()) {
            int length = value.textValue().codePointCount(0, value.textValue().length());
            if (field.minLength() != null && length < field.minLength()
                    || field.maxLength() != null && length > field.maxLength()) {
                throw invalid("字段 " + field.name() + " 长度不合法");
            }
        }
        if (value.isNumber()) {
            double number = value.doubleValue();
            if (field.min() != null && number < field.min()) {
                throw invalid("字段 " + field.name() + " 小于最小值");
            }
            if (field.max() != null && number > field.max()) {
                throw invalid("字段 " + field.name() + " 大于最大值");
            }
        }
        if (field.required() && value.isArray() && value.isEmpty()) {
            throw invalid("字段 " + field.name() + " 为必填项");
        }
    }

    private ObjectNode parseObject(String json, int maxBytes, String label) {
        if (json == null || json.isBlank()) {
            throw invalid(label + " 不能为空");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw invalid(label + " 超过大小限制");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!(node instanceof ObjectNode object)) {
                throw invalid(label + " 必须是 JSON 对象");
            }
            return object;
        } catch (JsonProcessingException ex) {
            throw invalid(label + " 不是合法 JSON");
        }
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            return null;
        }
        return node.textValue().trim();
    }

    private static boolean isEmpty(JsonNode value) {
        return value == null || value.isNull() || value.isTextual() && value.textValue().isBlank()
                || value.isArray() && value.isEmpty() || value.isObject() && value.isEmpty();
    }

    private static Integer integer(JsonNode node, String option, String field) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber()) throw invalid("字段 " + field + " 的 " + option + " 必须是整数");
        return node.intValue();
    }

    private static Double decimal(JsonNode node, String option, String field) {
        if (node == null || node.isNull()) return null;
        if (!node.isNumber()) throw invalid("字段 " + field + " 的 " + option + " 必须是数字");
        double value = node.doubleValue();
        if (!Double.isFinite(value)) throw invalid("字段 " + field + " 的 " + option + " 不合法");
        return value;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Compiled(ObjectNode source, Map<String, FieldRule> fields) {
    }

    public record FieldRule(String name, String label, String type, ValueKind kind, boolean required,
                            Integer minLength, Integer maxLength, Double min, Double max) {
    }

    public enum ValueKind {
        STRING, NUMBER, BOOLEAN, ARRAY, SCALAR
    }
}
