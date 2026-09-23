package com.lotus.bixi.workflow.api.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * Lenient, redacted correlation metadata for operator recovery reports.
 * Unlike {@link WorkflowEventCodec}, this parser deliberately accepts unknown event versions so
 * a quarantined future-version message remains diagnosable without exposing its payload body.
 */
public record WorkflowRecoveryMetadata(String processInstanceId, String businessTable,
        Long businessId, String businessKey, Integer round, String requestId,
        String operationId, String compensationId) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static Optional<WorkflowRecoveryMetadata> parse(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = JSON.readTree(payloadJson);
            if (root == null || !root.isObject()) {
                return Optional.empty();
            }
            JsonNode payload = root.path("payload");
            return Optional.of(new WorkflowRecoveryMetadata(
                    text(root, "processInstanceId"),
                    text(root, "businessTable"),
                    longValue(root, "businessId"),
                    text(root, "businessKey"),
                    integer(root, "round"),
                    text(root, "commandId"),
                    payload.isObject() ? text(payload, "operationId") : null,
                    payload.isObject() ? text(payload, "compensationId") : null));
        }
        catch (Exception invalidPayload) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue() : null;
    }

    private static Long longValue(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToLong()
                ? value.longValue() : null;
    }

    private static Integer integer(JsonNode object, String field) {
        JsonNode value = object.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt()
                ? value.intValue() : null;
    }
}
