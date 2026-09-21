package com.lotus.bixi.common.mq.reliable;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Objects;

/** Explicit source/type/schema registry for one target; reception and recovery execute the same handlers. */
public final class InboxExecutor {
    private final JdbcInboxStore store;
    private final String targetOwner;
    private final Map<Route, DurableMessageHandler> handlers;

    public InboxExecutor(JdbcInboxStore store, String targetOwner, Map<Route, DurableMessageHandler> handlers) {
        this.store = Objects.requireNonNull(store, "Inbox store is required");
        DurableMessage.requireOwner(targetOwner);
        this.targetOwner = targetOwner;
        this.handlers = Map.copyOf(handlers);
    }

    public String targetOwner() {
        return targetOwner;
    }

    /** Return only after a PROCESSED/IGNORED transaction has committed, including a verified duplicate. */
    public DurableMessageHandler.Result receive(DurableMessage message) {
        store.requireOutsideTransaction();
        handlerFor(message); // Validate routing BEFORE even looking up a previously successful event ID.
        JdbcInboxStore.Snapshot saved = store.accept(message);
        DurableMessageHandler.Result prior = committedResult(saved);
        if (prior != null) {
            return prior;
        }
        JdbcInboxStore.Lease lease = store.claim(targetOwner, message.eventId());
        if (lease == null) {
            prior = committedResult(store.find(targetOwner, message.eventId()));
            if (prior != null) {
                return prior;
            }
            throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                    "Inbox message is awaiting retry or belongs to another active worker");
        }
        return execute(lease);
    }

    /** Old or expired leases are rejected before handler entry, including recovery caller mistakes. */
    public DurableMessageHandler.Result execute(JdbcInboxStore.Lease lease) {
        store.requireOutsideTransaction();
        Objects.requireNonNull(lease, "Lease is required");
        requireTarget(lease.message()); // Wrong-owner leases must not enter even the failure CAS path.
        try {
            return store.process(lease, handlerFor(lease.message()));
        }
        catch (RuntimeException failure) {
            // TransactionTemplate has already completed rollback/cleanup here. Do not swallow
            // failure-evidence storage errors: transport cannot confirm takeover without them.
            store.markFailed(lease, failure);
            if (failure instanceof InboxDeliveryException delivery) {
                throw delivery;
            }
            throw new InboxDeliveryException(InboxDeliveryException.Kind.RETRYABLE,
                    "Inbox processing failed; retry remains persistent");
        }
    }

    /**
     * Explicit recovery with a total candidate budget, including rows moved to FAILED. Each
     * iteration claims at most one executable message; cleanup cannot reserve a waiting batch.
     * Returns handler attempts, which can be zero when the budget was spent on terminal cleanup.
     */
    public int recoverOnce(int capacity) {
        store.requireOutsideTransaction();
        if (capacity < 1 || capacity > 20) {
            throw new IllegalArgumentException("Recovery capacity must be between 1 and 20");
        }
        int attempted = 0;
        int scanned = 0;
        while (scanned < capacity) {
            var batch = store.claimBatch(targetOwner, 1);
            if (batch.scanned() == 0) {
                break;
            }
            scanned += batch.scanned();
            if (batch.leases().isEmpty()) {
                continue;
            }
            attempted++;
            try {
                execute(batch.leases().get(0));
            }
            catch (InboxDeliveryException recordedFailure) {
                // Failure is durable (or the lease was replaced); continue with other due rows.
            }
        }
        return attempted;
    }

    private DurableMessageHandler handlerFor(DurableMessage message) {
        requireTarget(message);
        DurableMessageHandler handler = handlers.get(new Route(message.sourceOwner(), message.type(), message.schemaVersion()));
        if (handler == null) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Message source, type or schema is not registered for this target");
        }
        return handler;
    }

    private void requireTarget(DurableMessage message) {
        Objects.requireNonNull(message, "Message is required");
        if (!targetOwner.equals(message.targetOwner())) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, "Message target is not registered here");
        }
    }

    private static DurableMessageHandler.Result committedResult(JdbcInboxStore.Snapshot saved) {
        if (saved == null) {
            return null;
        }
        return switch (saved.state()) {
            case PROCESSED -> DurableMessageHandler.Result.PROCESSED;
            case IGNORED -> DurableMessageHandler.Result.IGNORED;
            case FAILED -> throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Inbox processing is failed and requires explicit recovery");
            default -> null;
        };
    }

    static void requireNoAmbientTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Inbox delivery and recovery must run outside source business transactions");
        }
    }

    /** Full allowed route; a hash is integrity data and is not proof of source authentication. */
    public record Route(String sourceOwner, String type, int schemaVersion) {
        public Route {
            DurableMessage.requireOwner(sourceOwner);
            if (type == null || !type.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}") || schemaVersion < 1) {
                throw new IllegalArgumentException("Invalid inbox handler route");
            }
        }
    }
}
