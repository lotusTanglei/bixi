package com.lotus.bixi.common.mq.reliable;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicitly invoked sequential dispatcher; the application owns polling and lifecycle.
 * A send timeout requests interruption. If a transport ignores it, this dispatcher will not
 * reserve further leases until the old send exits. Replay identity always comes from the DB.
 */
public final class OutboxDispatcher implements AutoCloseable {

    private final JdbcOutboxStore store;
    private final String sourceOwner;
    private final DurableTransport transport;
    private final ReliableDeliveryProperties properties;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicBoolean sending = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Attempt> active = new AtomicReference<>();
    private final ThreadPoolExecutor sender;

    public OutboxDispatcher(JdbcOutboxStore store, String sourceOwner, DurableTransport transport,
            ReliableDeliveryProperties properties) {
        this.store = Objects.requireNonNull(store, "Outbox store is required");
        DurableMessage.requireOwner(sourceOwner);
        this.sourceOwner = sourceOwner;
        this.transport = Objects.requireNonNull(transport, "Transport is required");
        this.properties = Objects.requireNonNull(properties, "Delivery properties are required");
        // This one slot holds only a control task, never a claimed message. It bridges the
        // worker's completion handoff without a SynchronousQueue readiness race.
        this.sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, "outbox-send-" + sourceOwner);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Attempt at most batchSize sends, claiming only on an admitted sender thread.
     * Returns the number attempted, including failed or fenced attempts. It does not poll
     * automatically. Call outside business transactions and close during application shutdown.
     */
    public int dispatchOnce() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox dispatch must run outside a business transaction");
        }
        if (closed.get() || !polling.compareAndSet(false, true)) {
            return 0;
        }
        try {
            int attempted = 0;
            while (attempted < properties.batchSize() && !closed.get() && !sending.get()) {
                Attempt attempt = new Attempt();
                sending.set(true);
                active.set(attempt);
                if (closed.get()) {
                    attempt.cancel();
                    sending.set(false);
                    active.compareAndSet(attempt, null);
                    break;
                }
                try {
                    sender.execute(attempt);
                }
                catch (RejectedExecutionException ex) {
                    attempt.cancel();
                    sending.set(false);
                    active.compareAndSet(attempt, null);
                    break; // No claim has run, so there is no delivery attempt to fail.
                }
                JdbcOutboxStore.Lease lease;
                try {
                    if (!attempt.awaitAdmission()) {
                        break;
                    }
                    // Once admitted, wait for the short claim transaction to commit before
                    // starting the send-result timeout. DB failures retain their original type.
                    lease = attempt.claimed.get();
                }
                catch (InterruptedException ex) {
                    attempt.cancel();
                    Thread.currentThread().interrupt();
                    break;
                }
                catch (CancellationException ex) {
                    break;
                }
                catch (ExecutionException ex) {
                    throw propagate(ex.getCause());
                }
                if (lease == null) {
                    break;
                }
                attempted++;
                try {
                    attempt.send.get(properties.sendTimeout().toNanos(), TimeUnit.NANOSECONDS);
                }
                catch (TimeoutException ex) {
                    attempt.cancel();
                    store.markFailed(lease, ex);
                    break;
                }
                catch (InterruptedException ex) {
                    attempt.cancel();
                    try {
                        store.markFailed(lease, ex);
                    }
                    finally {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
                catch (CancellationException ex) {
                    store.markFailed(lease, ex);
                    break;
                }
                catch (ExecutionException ex) {
                    store.markFailed(lease, ex.getCause());
                    continue;
                }
                // Deliberately outside the send catch: a DB mark failure leaves the original
                // IN_FLIGHT lease for recovery, preserving the unavoidable duplicate window.
                store.markDelivered(lease);
            }
            return attempted;
        }
        finally {
            polling.set(false);
        }
    }

    private static RuntimeException propagate(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Outbox claim failed", cause);
    }

    /** A queued attempt carries no message or lease until its sender wins admission. */
    private final class Attempt implements Runnable {
        private final CompletableFuture<Void> admitted = new CompletableFuture<>();
        private final CompletableFuture<JdbcOutboxStore.Lease> claimed = new CompletableFuture<>();
        private final FutureTask<Void> send = new FutureTask<>(() -> {
            // A timeout/close wins by completing this same gate exceptionally. A canceled
            // queued task can therefore never wake up later and silently acquire a lease.
            if (closed.get() || !admitted.complete(null)) {
                claimed.complete(null);
                return null;
            }
            try {
                if (closed.get() || Thread.currentThread().isInterrupted()) {
                    claimed.complete(null);
                    return null;
                }
                var leases = store.claim(sourceOwner, 1);
                var lease = leases.isEmpty() ? null : leases.get(0);
                claimed.complete(lease);
                if (lease != null) {
                    if (closed.get() || Thread.currentThread().isInterrupted()) {
                        throw new CancellationException("Outbox sender closed after claim");
                    }
                    transport.deliver(lease.message());
                }
                return null;
            }
            catch (RuntimeException | Error ex) {
                claimed.completeExceptionally(ex);
                throw ex;
            }
        }) {
            @Override
            protected void done() {
                if (isCancelled()) {
                    var cancellation = new CancellationException("Outbox send canceled");
                    admitted.completeExceptionally(cancellation);
                    claimed.completeExceptionally(cancellation);
                }
            }
        };

        private boolean awaitAdmission() throws InterruptedException, ExecutionException {
            try {
                admitted.get(properties.sendTimeout().toNanos(), TimeUnit.NANOSECONDS);
            }
            catch (TimeoutException ex) {
                if (admitted.completeExceptionally(ex)) {
                    cancel();
                    return false;
                }
                admitted.get(); // Admission won the race; only the DB claim is now pending.
            }
            return true;
        }

        private void cancel() {
            send.cancel(true);
        }

        @Override
        public void run() {
            try {
                send.run();
            }
            finally {
                active.compareAndSet(this, null);
                sending.set(false);
            }
        }
    }

    @Override
    public void close() {
        closed.set(true);
        Attempt attempt = active.get();
        if (attempt != null) {
            attempt.cancel();
        }
        sender.shutdownNow();
    }
}
