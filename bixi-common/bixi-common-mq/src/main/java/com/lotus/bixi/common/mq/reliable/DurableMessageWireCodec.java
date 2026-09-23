package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Objects;

/**
 * Encode and decode a {@link DurableMessage} to and from the exact bytes placed on the wire.
 * The codec is stateless; callers may reuse one instance across threads.
 */
public final class DurableMessageWireCodec {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .build();

    public byte[] encode(DurableMessage message) {
        Objects.requireNonNull(message, "Message is required");
        try {
            return JSON.writeValueAsBytes(new WireEnvelope(
                    message.sourceOwner(), message.targetOwner(), message.eventId(),
                    message.type(), message.schemaVersion(),
                    message.payloadJson(), message.payloadHash()));
        }
        catch (IOException ex) {
            throw new IllegalStateException("Durable message encoding failed", ex);
        }
    }

    public DurableMessage decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Bytes are required");
        try {
            WireEnvelope envelope = JSON.readValue(bytes, WireEnvelope.class);
            return new DurableMessage(envelope.sourceOwner, envelope.targetOwner, envelope.eventId,
                    envelope.type, envelope.schemaVersion, envelope.payloadJson, envelope.payloadHash);
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("Durable message wire format is invalid", ex);
        }
    }

    private record WireEnvelope(String sourceOwner, String targetOwner, String eventId,
            String type, int schemaVersion, String payloadJson, String payloadHash) {
    }
}
