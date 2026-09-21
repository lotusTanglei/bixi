package com.lotus.bixi.workflow.api.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** JSON variables preserve numbers at HTTP/Feign boundaries, independent of display serializers. */
public final class WorkflowJsonVariables {
    private WorkflowJsonVariables() { }
    private static final ObjectMapper EXACT_JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);

    public static class Deserializer extends JsonDeserializer<Map<String, Object>> {
        @Override public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            Map<String, Object> variables = EXACT_JSON.readValue(parser, new TypeReference<Map<String, Object>>() { });
            try {
                validateVariables(variables);
            } catch (IllegalArgumentException invalid) {
                throw JsonMappingException.from(parser, invalid.getMessage(), invalid);
            }
            return variables;
        }
    }

    public static String requireUnicodeScalars(String text) {
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) {
                    throw new WorkflowInvalidUnicodeException();
                }
            } else if (Character.isLowSurrogate(current)) {
                throw new WorkflowInvalidUnicodeException();
            }
        }
        return text;
    }

    private static void validateVariables(Object value) {
        if (value instanceof String text) requireUnicodeScalars(text);
        else if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                requireUnicodeScalars((String) key);
                validateVariables(item);
            });
        } else if (value instanceof List<?> list) list.forEach(WorkflowJsonVariables::validateVariables);
    }

    public static class Serializer extends JsonSerializer<Map<String, Object>> {
        @Override public void serialize(Map<String, Object> value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            write(value, generator);
        }
        private void write(Object value, JsonGenerator out) throws IOException {
            if (value == null) { out.writeNull(); return; }
            if (value instanceof JsonNode node) {
                if (node.isNull()) { out.writeNull(); return; }
                if (node.isNumber()) { write(node.numberValue(), out); return; }
                if (node.isTextual()) { out.writeString(requireUnicodeScalars(node.textValue())); return; }
                if (node.isBoolean()) { out.writeBoolean(node.booleanValue()); return; }
                if (node.isArray()) {
                    out.writeStartArray();
                    for (JsonNode element : node) write(element, out);
                    out.writeEndArray(); return;
                }
                if (node.isObject()) {
                    out.writeStartObject();
                    var fields = node.fields();
                    while (fields.hasNext()) {
                        var field = fields.next(); out.writeFieldName(requireUnicodeScalars(field.getKey())); write(field.getValue(), out);
                    }
                    out.writeEndObject(); return;
                }
            }
            if (value instanceof Map<?, ?> map) {
                out.writeStartObject();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw JsonMappingException.from(out, "JSON对象键必须是字符串");
                    out.writeFieldName(requireUnicodeScalars(key)); write(entry.getValue(), out);
                }
                out.writeEndObject(); return;
            }
            if (value instanceof List<?> list) {
                out.writeStartArray();
                for (Object element : list) write(element, out);
                out.writeEndArray(); return;
            }
            if (value instanceof String text) { out.writeString(requireUnicodeScalars(text)); return; }
            if (value instanceof Boolean bool) { out.writeBoolean(bool); return; }
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof BigInteger || value instanceof BigDecimal
                    || (value instanceof Float number && Float.isFinite(number))
                    || (value instanceof Double number && Double.isFinite(number))) {
                out.writeNumber(value.toString()); return;
            }
            throw JsonMappingException.from(out, "流程变量只允许JSON值和有限数字");
        }
    }
}
