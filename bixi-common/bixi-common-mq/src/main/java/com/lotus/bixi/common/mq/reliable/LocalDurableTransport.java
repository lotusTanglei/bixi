package com.lotus.bixi.common.mq.reliable;

import java.util.Map;
import java.util.Objects;

/** Same inbox execution path as recovery; local delivery acknowledges only committed business success. */
public final class LocalDurableTransport implements DurableTransport {
    private final Map<String, InboxExecutor> executors;

    public LocalDurableTransport(Map<String, InboxExecutor> executors) {
        this.executors = Map.copyOf(executors);
        this.executors.forEach((owner, executor) -> {
            if (!owner.equals(executor.targetOwner())) {
                throw new IllegalArgumentException("Local delivery registry key must match its executor target");
            }
        });
    }

    @Override
    public void deliver(DurableMessage message) {
        InboxExecutor.requireNoAmbientTransaction();
        Objects.requireNonNull(message, "Message is required");
        InboxExecutor executor = executors.get(message.targetOwner());
        if (executor == null) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE, "No local executor is registered for target");
        }
        executor.receive(message);
    }
}
