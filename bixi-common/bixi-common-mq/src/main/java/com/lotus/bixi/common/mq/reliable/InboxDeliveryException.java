package com.lotus.bixi.common.mq.reliable;

import java.util.Objects;

/** Explicit non-success delivery outcome; messages must not contain payloads or credentials. */
public final class InboxDeliveryException extends RuntimeException {
    public enum Kind { RETRYABLE, PERMANENT, CONFLICT }

    private final Kind kind;

    public InboxDeliveryException(Kind kind, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "Failure kind is required");
    }

    public Kind kind() {
        return kind;
    }
}
