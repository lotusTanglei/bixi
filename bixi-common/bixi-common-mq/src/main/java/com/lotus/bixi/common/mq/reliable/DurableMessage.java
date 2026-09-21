package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable envelope. The hash covers canonical JSON and its routing/schema context. */
public record DurableMessage(String sourceOwner, String targetOwner, String eventId, String type,
        int schemaVersion, String payloadJson, String payloadHash) {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    public DurableMessage {
        requireOwner(sourceOwner);
        requireOwner(targetOwner);
        if (eventId == null || !UUID.fromString(eventId).toString().equals(eventId)) {
            throw new IllegalArgumentException("eventId must be a canonical lowercase UUID");
        }
        if (type == null || !type.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")) {
            throw new IllegalArgumentException("Invalid message type");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        payloadJson = canonicalize(payloadJson);
        String expected = hash(sourceOwner, targetOwner, type, schemaVersion, payloadJson);
        if (!expected.equals(payloadHash)) {
            throw new IllegalArgumentException("Message payload hash mismatch");
        }
    }

    public static DurableMessage create(String sourceOwner, String targetOwner, String eventId,
            String type, int schemaVersion, String payloadJson) {
        String canonical = canonicalize(payloadJson);
        return new DurableMessage(sourceOwner, targetOwner, eventId, type, schemaVersion, canonical,
                hash(sourceOwner, targetOwner, type, schemaVersion, canonical));
    }

    static void requireOwner(String owner) {
        if (owner == null || !owner.matches("[a-z][a-z0-9_-]{0,63}")) {
            throw new IllegalArgumentException("Invalid message owner");
        }
    }

    private static String canonicalize(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Message JSON is required");
        }
        try {
            return canonicalNode(JSON.readTree(payload));
        }
        catch (JsonProcessingException ex) {
            // Parser diagnostics may contain the payload; deliberately do not retain the cause.
            throw new IllegalArgumentException("Invalid message JSON");
        }
    }

    private static String canonicalNode(JsonNode node) throws JsonProcessingException {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            StringBuilder out = new StringBuilder("{");
            for (var entry : sorted.entrySet()) {
                if (out.length() > 1) {
                    out.append(',');
                }
                requireValidUnicode(entry.getKey());
                out.append(JSON.writeValueAsString(entry.getKey())).append(':').append(canonicalNode(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (node.isArray()) {
            StringBuilder out = new StringBuilder("[");
            for (JsonNode value : node) {
                if (out.length() > 1) {
                    out.append(',');
                }
                out.append(canonicalNode(value));
            }
            return out.append(']').toString();
        }
        if (node.isNumber()) {
            return node.decimalValue().stripTrailingZeros().toString();
        }
        if (node.isTextual()) {
            requireValidUnicode(node.textValue());
        }
        return JSON.writeValueAsString(node);
    }

    private static void requireValidUnicode(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException("Invalid Unicode in message JSON");
                }
            }
            else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Invalid Unicode in message JSON");
            }
        }
    }

    private static String hash(String source, String target, String type, int schema, String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String field : new String[] {source, target, type, Integer.toString(schema), canonical}) {
                byte[] bytes = Objects.requireNonNull(field, "Message field is required").getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    @Override
    public String toString() {
        return "DurableMessage[sourceOwner=" + sourceOwner + ", targetOwner=" + targetOwner
                + ", eventId=" + eventId + ", type=" + type + ", schemaVersion=" + schemaVersion + "]";
    }
}
