package com.lotus.bixi.common.mq.reliable;

/** Return only after this adapter has confirmed durable handoff; throw on failure or unknown outcome. */
@FunctionalInterface
public interface DurableTransport {
    void deliver(DurableMessage message);
}
