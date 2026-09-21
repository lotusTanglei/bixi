package com.lotus.bixi.common.mq.reliable;
public final class OutboxConflictException extends RuntimeException {
    public OutboxConflictException() { super("Outbox identity or deduplication key conflicts with immutable content"); }
}
