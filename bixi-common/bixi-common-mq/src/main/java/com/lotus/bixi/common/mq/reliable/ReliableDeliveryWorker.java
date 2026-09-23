package com.lotus.bixi.common.mq.reliable;

import org.springframework.context.SmartLifecycle;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the polling lifecycle for one business owner. Claims and handler execution are
 * delegated to the durable stores; this class only schedules work outside request
 * transactions and closes the dispatcher before the executor exits.
 */
public final class ReliableDeliveryWorker implements SmartLifecycle, AutoCloseable {
    private final OutboxDispatcher dispatcher;
    private final InboxExecutor inbox;
    private final ReliableDeliveryProperties properties;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean();

    public ReliableDeliveryWorker(OutboxDispatcher dispatcher, InboxExecutor inbox,
            ReliableDeliveryProperties properties) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "Dispatcher is required");
        this.inbox = Objects.requireNonNull(inbox, "Inbox executor is required");
        this.properties = Objects.requireNonNull(properties, "Delivery properties are required");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "reliable-delivery-" + inbox.targetOwner());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        long delay = properties.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::poll, 0, delay, TimeUnit.MILLISECONDS);
    }

    private void poll() {
        if (!running.get()) return;
        try {
            dispatcher.dispatchOnce();
            inbox.recoverOnce(properties.batchSize());
        }
        catch (RuntimeException ignored) {
            // The stores persist lease/retry responsibility. The next poll retries.
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        dispatcher.close();
        scheduler.shutdownNow();
    }

    @Override
    public void stop(Runnable callback) {
        try { stop(); }
        finally { callback.run(); }
    }

    @Override public boolean isRunning() { return running.get(); }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return 0; }

    @Override
    public void close() {
        stop();
    }
}
