package com.lotus.bixi.common.mq.reliable;

/**
 * Runs in the owning target's local inbox transaction. Business writes and successor outbox
 * enqueue must join that same transaction and DataSource; do not send network requests here.
 * Throw InboxDeliveryException(PERMANENT, ...) for deterministic rejection, otherwise failures retry.
 */
@FunctionalInterface
public interface DurableMessageHandler {
    Result handle(DurableMessage message);

    enum Result { PROCESSED, IGNORED }
}
