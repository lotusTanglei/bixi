package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class OutboxDispatcherTest extends MysqlOutboxTestSupport {
    @Test
    void sendRunsAfterClaimCommitOutsideAnyTransactionAndOnlyReservesActualCapacity() {
        var messages = List.of(message("upms", 1), message("upms", 2), message("upms", 3));
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO outbox_test_business VALUES (1)");
            for (var event : messages) store.enqueue(event, event.eventId(), null, null);
        });
        var observed = Collections.synchronizedList(new ArrayList<DurableMessage>());
        try (var dispatcher = new OutboxDispatcher(store, "upms", event -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(count("outbox_test_business")).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox WHERE status='IN_FLIGHT'", Integer.class)).isOne();
            observed.add(event);
        }, properties)) {
            assertThat(dispatcher.dispatchOnce()).isEqualTo(3);
            assertThat(dispatcher.dispatchOnce()).isZero();
        }
        assertThat(observed).containsExactlyInAnyOrderElementsOf(messages);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox WHERE status='DELIVERED'", Integer.class)).isEqualTo(3);
    }

    @Test
    void failedTransportPersistsRetryAndReconstructedDispatcherReplaysTheExactMessage() {
        var event = message("upms", 1); enqueue(event, "key");
        try (var dispatcher = new OutboxDispatcher(store, "upms", ignored -> { throw new IllegalStateException("secret payload"); }, properties)) {
            assertThat(dispatcher.dispatchOnce()).isOne();
            assertThat(state(event)).isEqualTo("PENDING");
        }
        makeDue();
        var seen = Collections.synchronizedList(new ArrayList<DurableMessage>());
        try (var restarted = new OutboxDispatcher(new JdbcOutboxStore(dataSource, manager, properties), "upms", seen::add, properties)) {
            assertThat(restarted.dispatchOnce()).isOne();
        }
        assertThat(seen).containsExactly(event);
        assertThat(state(event)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox", Integer.class)).isEqualTo(2);
    }

    @Test
    void transportSuccessFollowedByDatabaseMarkFailureReplaysTheSameEventAfterRestart() {
        var event = message("upms", 1); enqueue(event, "key");
        var seen = Collections.synchronizedList(new ArrayList<DurableMessage>());
        jdbc.execute("""
                CREATE TRIGGER outbox_test_lose_mark BEFORE UPDATE ON reliable_outbox FOR EACH ROW
                BEGIN
                    IF NEW.status = 'DELIVERED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'simulated lost completion';
                    END IF;
                END
                """);
        try (var dispatcher = new OutboxDispatcher(store, "upms", seen::add, properties)) {
            assertThatThrownBy(dispatcher::dispatchOnce).isInstanceOf(DataAccessException.class);
            assertThat(state(event)).isEqualTo("IN_FLIGHT");
            assertThat(seen).containsExactly(event);
        }
        finally { jdbc.execute("DROP TRIGGER outbox_test_lose_mark"); }
        expireLeases();
        try (var restarted = new OutboxDispatcher(new JdbcOutboxStore(dataSource, manager, properties), "upms", seen::add, properties)) {
            assertThat(restarted.dispatchOnce()).isOne();
        }
        assertThat(seen).containsExactly(event, event);
        assertThat(state(event)).isEqualTo("DELIVERED");
    }

    @Test
    void timeoutPersistsFailureAndAnUninterruptibleSendPreventsFurtherClaims() throws Exception {
        var event = message("upms", 1); enqueue(event, "first"); enqueue(message("upms", 2), "second");
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var fast = new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofMillis(200), 20, 12);
        try (var dispatcher = new OutboxDispatcher(store, "upms", ignored -> {
            attempts.incrementAndGet(); started.countDown();
            try {
                while (release.getCount() != 0) {
                    try { release.await(); }
                    catch (InterruptedException ignoredInterrupt) { /* Simulate a non-cooperative transport. */ }
                }
            }
            finally { returned.countDown(); }
        }, fast)) {
            assertThat(dispatcher.dispatchOnce()).isOne();
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_outbox WHERE last_error='java.util.concurrent.TimeoutException'", Integer.class)).isOne();
            makeDue();
            assertThat(dispatcher.dispatchOnce()).isZero();
            assertThat(attempts).hasValue(1);
            assertThat(jdbc.queryForObject("SELECT SUM(attempts) FROM reliable_outbox", Integer.class)).isOne();
        }
        finally { release.countDown(); assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void dispatcherRejectsAnAmbientBusinessTransactionBeforeAnyClaimOrSend() {
        var event = message("upms", 1); enqueue(event, "key");
        var sends = new AtomicInteger();
        try (var dispatcher = new OutboxDispatcher(store, "upms", ignored -> sends.incrementAndGet(), properties)) {
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> dispatcher.dispatchOnce())).isInstanceOf(IllegalStateException.class);
            assertThat(sends).hasValue(0);
            assertThat(state(event)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox", Integer.class)).isZero();
        }
    }
    @Test
    void executorCompletionHandoffDoesNotClaimBeforeTheSenderActuallyRuns() throws Exception {
        var one = new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(5), 1, 1);
        var oneStore = new JdbcOutboxStore(dataSource, manager, one);
        var first = message("upms", 1); enqueue(first, "first");
        var sends = new AtomicInteger();
        var finishFirst = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var secondSubmitted = new CountDownLatch(1);
        var caller = Executors.newSingleThreadExecutor();
        try (var dispatcher = new OutboxDispatcher(oneStore, "upms", ignored -> sends.incrementAndGet(), one)) {
            installHandoffBarrier(dispatcher, finishFirst, releaseWorker, secondSubmitted, new CountDownLatch(0));
            assertThat(dispatcher.dispatchOnce()).isOne();
            assertThat(finishFirst.await(2, TimeUnit.SECONDS)).isTrue();
            var second = message("upms", 2); enqueue(second, "second");
            var next = caller.submit(dispatcher::dispatchOnce);
            assertThat(secondSubmitted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(state(second)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox WHERE event_id=?", Integer.class, second.eventId())).isZero();
            releaseWorker.countDown();
            assertThat(next.get(5, TimeUnit.SECONDS)).isOne();
            assertThat(sends).hasValue(2);
            assertThat(state(second)).isEqualTo("DELIVERED");
        }
        finally { releaseWorker.countDown(); caller.shutdownNow(); }
    }

    @Test
    void queuedControlTaskTimeoutCannotClaimLaterOrConsumeAnAttempt() throws Exception {
        var one = new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofMillis(200), 1, 1);
        var oneStore = new JdbcOutboxStore(dataSource, manager, one);
        enqueue(message("upms", 1), "first");
        var sends = new AtomicInteger();
        var finishFirst = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var drained = new CountDownLatch(1);
        try (var dispatcher = new OutboxDispatcher(oneStore, "upms", ignored -> sends.incrementAndGet(), one)) {
            installHandoffBarrier(dispatcher, finishFirst, releaseWorker, new CountDownLatch(0), drained);
            assertThat(dispatcher.dispatchOnce()).isOne();
            assertThat(finishFirst.await(2, TimeUnit.SECONDS)).isTrue();
            var second = message("upms", 2); enqueue(second, "second");
            assertThat(dispatcher.dispatchOnce()).isZero();
            assertThat(state(second)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox WHERE event_id=?", Integer.class, second.eventId())).isZero();
            releaseWorker.countDown();
            assertThat(drained.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(sends).hasValue(1);
            assertThat(state(second)).isEqualTo("PENDING");
            assertThat(dispatcher.dispatchOnce()).isOne();
            assertThat(state(second)).isEqualTo("DELIVERED");
        }
        finally { releaseWorker.countDown(); }
    }

    @Test
    void closeCancelsQueuedControlWithoutAcquiringOrLosingALease() throws Exception {
        var one = new ReliableDeliveryProperties(Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofSeconds(5), 1, 1);
        var oneStore = new JdbcOutboxStore(dataSource, manager, one);
        enqueue(message("upms", 1), "first");
        var sends = new AtomicInteger();
        var finishFirst = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var secondSubmitted = new CountDownLatch(1);
        var caller = Executors.newSingleThreadExecutor();
        try (var dispatcher = new OutboxDispatcher(oneStore, "upms", ignored -> sends.incrementAndGet(), one)) {
            installHandoffBarrier(dispatcher, finishFirst, releaseWorker, secondSubmitted, new CountDownLatch(0));
            assertThat(dispatcher.dispatchOnce()).isOne();
            assertThat(finishFirst.await(2, TimeUnit.SECONDS)).isTrue();
            var second = message("upms", 2); enqueue(second, "second");
            var next = caller.submit(dispatcher::dispatchOnce);
            assertThat(secondSubmitted.await(2, TimeUnit.SECONDS)).isTrue();
            dispatcher.close();
            assertThat(next.get(2, TimeUnit.SECONDS)).isZero();
            releaseWorker.countDown();
            assertThat(sends).hasValue(1);
            assertThat(state(second)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox WHERE event_id=?", Integer.class, second.eventId())).isZero();
        }
        finally { releaseWorker.countDown(); caller.shutdownNow(); }
    }

    private void installHandoffBarrier(OutboxDispatcher dispatcher, CountDownLatch finishedFirst,
            CountDownLatch releaseWorker, CountDownLatch secondSubmitted, CountDownLatch drained) throws Exception {
        // Preserve the production queue: only stretch the executor's real completion handoff.
        var field = OutboxDispatcher.class.getDeclaredField("sender");
        field.setAccessible(true);
        var original = (ThreadPoolExecutor) field.get(dispatcher);
        original.shutdownNow();
        var submitted = new AtomicInteger();
        var finished = new AtomicInteger();
        var barrier = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, original.getQueue(), original.getThreadFactory()) {
            @Override public void execute(Runnable command) {
                int number = submitted.incrementAndGet();
                try { super.execute(command); }
                finally { if (number == 2) secondSubmitted.countDown(); }
            }
            @Override protected void afterExecute(Runnable task, Throwable failure) {
                if (finished.incrementAndGet() == 1) {
                    finishedFirst.countDown();
                    boolean interrupted = false;
                    while (releaseWorker.getCount() != 0) {
                        try { releaseWorker.await(); }
                        catch (InterruptedException ex) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                }
                else { drained.countDown(); }
            }
        };
        field.set(dispatcher, barrier);
    }

}
