package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class LocalDurableTransportTest extends MysqlInboxTestSupport {
    @Test
    void localSuccessThenLostSourceMarkReplaysWithoutDuplicatingBusinessOrReplyOutbox() {
        var event = message("upms", 1); enqueue(event, "submit");
        var sends = new AtomicInteger();
        var local = new LocalDurableTransport(Map.of("workflow", executor(message -> { sends.incrementAndGet(); return effect(message); })));
        var first = store.claim("upms", 1).get(0);
        local.deliver(first.message()); // Source crashes before marking DELIVERED.
        assertThat(state(event)).isEqualTo("IN_FLIGHT");
        assertThat(inboxState(event)).isEqualTo("PROCESSED");
        expireLeases();
        try (var restarted = new OutboxDispatcher(new JdbcOutboxStore(dataSource, manager, properties), "upms", local, properties)) {
            assertThat(restarted.dispatchOnce()).isOne();
        }
        assertThat(state(event)).isEqualTo("DELIVERED");
        assertThat(sends).hasValue(1);
        assertThat(count("inbox_test_business")).isOne();
        assertThat(count("reliable_outbox")).isEqualTo(2);
    }

    @Test
    void localPendingBusyFailedAndUnknownTargetNeverReturnSuccess() {
        var local = new LocalDurableTransport(Map.of("workflow", executor(message -> { throw new IllegalStateException(); })));
        var event = message("upms", 1);
        assertThatThrownBy(() -> local.deliver(event)).isInstanceOf(InboxDeliveryException.class);
        assertThatThrownBy(() -> local.deliver(event)).isInstanceOf(InboxDeliveryException.class);
        var busy = message("upms", 2); inbox.accept(busy); inbox.claim("workflow", busy.eventId());
        assertThatThrownBy(() -> local.deliver(busy)).isInstanceOf(InboxDeliveryException.class);
        var unknown = DurableMessage.create("upms", "unknown", event.eventId(), event.type(), 1, event.payloadJson());
        assertThatThrownBy(() -> local.deliver(unknown)).isInstanceOf(InboxDeliveryException.class);
        var failed = message("upms", 3);
        var permanent = new LocalDurableTransport(Map.of("workflow", executor(message -> { throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, "rejected"); })));
        assertThatThrownBy(() -> permanent.deliver(failed)).isInstanceOf(InboxDeliveryException.class);
        assertThatThrownBy(() -> local.deliver(failed)).isInstanceOf(InboxDeliveryException.class);
    }

    @Test
    void ambientSourceTransactionAndAfterCommitResourcesAreRejectedBeforeTargetTakeover() {
        var local = new LocalDurableTransport(Map.of("workflow", executor(this::effect)));
        var event = message("upms", 1);
        transaction.executeWithoutResult(status -> {
            assertThatThrownBy(() -> local.deliver(event)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> executor(this::effect).receive(event)).isInstanceOf(IllegalStateException.class);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
                    assertThatThrownBy(() -> local.deliver(event)).isInstanceOf(IllegalStateException.class);
                }
            });
        });
        assertThat(count("reliable_inbox")).isZero();
        assertThat(count("inbox_test_business")).isZero();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
        local.deliver(event);
        assertThat(inboxState(event)).isEqualTo("PROCESSED");
    }
    @Test
    void residualBoundConnectionIsRejectedEvenWhenTransactionFlagsHaveBeenCleared() throws Exception {
        var local = new LocalDurableTransport(Map.of("workflow", executor(this::effect)));
        var event = message("upms", 1);
        try (var connection = dataSource.getConnection()) {
            TransactionSynchronizationManager.bindResource(dataSource, new org.springframework.jdbc.datasource.ConnectionHolder(connection));
            try {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                assertThatThrownBy(() -> local.deliver(event)).isInstanceOf(IllegalStateException.class);
            }
            finally { TransactionSynchronizationManager.unbindResource(dataSource); }
        }
        assertThat(count("reliable_inbox")).isZero();
        local.deliver(event);
        assertThat(inboxState(event)).isEqualTo("PROCESSED");
    }

}
