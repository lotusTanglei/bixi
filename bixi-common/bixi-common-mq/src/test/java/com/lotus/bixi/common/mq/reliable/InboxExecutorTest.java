package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class InboxExecutorTest extends MysqlInboxTestSupport {
    @Test
    void businessInboxAndFollowupOutboxCommitAtomicallyAndReplayOnlyAfterSuccess() {
        var event = message("upms", 1);
        var calls = new AtomicInteger();
        var executor = executor(message -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isTrue();
            calls.incrementAndGet();
            return effect(message);
        });
        assertThat(executor.receive(event)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
        assertThat(executor.receive(event)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
        assertThat(calls).hasValue(1);
        assertThat(inboxState(event)).isEqualTo("PROCESSED");
        assertThat(count("inbox_test_business")).isOne();
        assertThat(count("reliable_outbox")).isOne();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    }

    @Test
    void handlerFailureRollsBackBusinessAndOutboxThenPersistsRetryOutsideThatTransaction() {
        var event = message("upms", 1);
        var failing = executor(message -> { effect(message); throw new IllegalStateException("secret payload credential"); });
        assertThatThrownBy(() -> failing.receive(event)).isInstanceOf(InboxDeliveryException.class);
        assertThat(inboxState(event)).isEqualTo("RECEIVED");
        assertThat(count("inbox_test_business")).isZero();
        assertThat(count("reliable_outbox")).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_inbox", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT last_error FROM reliable_inbox", String.class)).isEqualTo("java.lang.IllegalStateException");
        assertThatThrownBy(() -> executor(this::effect).receive(event)).isInstanceOf(InboxDeliveryException.class);
        assertThat(count("inbox_test_business")).isZero();
        inboxDue();
        var restarted = executor(new JdbcInboxStore(dataSource, manager, properties), "workflow", this::effect);
        assertThat(restarted.recoverOnce(20)).isOne();
        assertThat(inboxState(event)).isEqualTo("PROCESSED");
        assertThat(count("inbox_test_business")).isOne();
        assertThat(count("reliable_outbox")).isOne();
    }

    @Test
    void permanentFailureAndIgnoredAreDistinctCommittedOutcomes() {
        var event = message("upms", 1);
        var permanent = executor(message -> { effect(message); throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT, "invalid command"); });
        assertThatThrownBy(() -> permanent.receive(event)).isInstanceOf(InboxDeliveryException.class);
        assertThat(inboxState(event)).isEqualTo("FAILED");
        assertThat(count("inbox_test_business")).isZero();
        assertThat(count("reliable_outbox")).isZero();
        assertThatThrownBy(() -> executor(this::effect).receive(event)).isInstanceOf(InboxDeliveryException.class);
        assertThat(inbox.claim("workflow", 20)).isEmpty();
        var ignored = message("upms", 2);
        var calls = new AtomicInteger();
        var ignore = executor(message -> { calls.incrementAndGet(); return DurableMessageHandler.Result.IGNORED; });
        assertThat(ignore.receive(ignored)).isEqualTo(DurableMessageHandler.Result.IGNORED);
        assertThat(ignore.receive(ignored)).isEqualTo(DurableMessageHandler.Result.IGNORED);
        assertThat(inboxState(ignored)).isEqualTo("IGNORED");
        assertThat(calls).hasValue(1);
    }

    @Test
    void concurrentReceiversAndRecoveryShareTheSameLockedExecution() throws Exception {
        var event = message("upms", 1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var first = executor(message -> {
            calls.incrementAndGet();
            var result = effect(message);
            // Make the indexed lease eligible while the processing transaction owns its row lock.
            jdbc.update("UPDATE reliable_inbox SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE target_owner=? AND event_id=?", message.targetOwner(), message.eventId());
            entered.countDown(); await(release); return result;
        });
        var second = executor(new JdbcInboxStore(dataSource, manager, properties), "workflow", message -> { calls.incrementAndGet(); return effect(message); });
        var threads = Executors.newFixedThreadPool(2);
        try {
            var winning = threads.submit(() -> first.receive(event));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(inbox.claim("workflow", 20)).isEmpty();
            var duplicate = threads.submit(() -> second.receive(event));
            release.countDown();
            assertThat(winning.get(10, TimeUnit.SECONDS)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
            assertThat(duplicate.get(10, TimeUnit.SECONDS)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
            assertThat(calls).hasValue(1);
            assertThat(count("inbox_test_business")).isOne();
            assertThat(count("reliable_outbox")).isOne();
        }
        finally { release.countDown(); threads.shutdownNow(); }
    }

    @Test
    void changedIdentityContentNeverOverwritesTheOriginalRecordOrRunsAHandler() {
        var event = message("upms", 1);
        var executor = executor(this::effect);
        executor.receive(event);
        var variations = List.of(
                DurableMessage.create("other", "workflow", event.eventId(), event.type(), 1, event.payloadJson()),
                DurableMessage.create("upms", "workflow", event.eventId(), event.type(), 1, "{\"changed\":true}"));
        for (var changed : variations) {
            assertThatThrownBy(() -> executor.receive(changed)).isInstanceOf(InboxDeliveryException.class)
                    .satisfies(ex -> assertThat(((InboxDeliveryException) ex).kind()).isEqualTo(InboxDeliveryException.Kind.CONFLICT));
        }
        assertThat(inbox.find("workflow", event.eventId()).message()).isEqualTo(event);
        assertThat(count("inbox_test_business")).isOne();
        assertThat(count("reliable_outbox")).isOne();
        assertThat(inboxState(event)).isEqualTo("PROCESSED");
    }

    @Test
    void targetSourceTypeAndSchemaAreValidatedBeforeTakeoverEvenForPreviouslySuccessfulIds() {
        var original = message("upms", 1);
        var executor = executor(this::effect);
        executor.receive(original);
        var rejected = List.of(
                DurableMessage.create("upms", "other", original.eventId(), original.type(), 1, original.payloadJson()),
                DurableMessage.create("unknown", "workflow", original.eventId(), original.type(), 1, original.payloadJson()),
                DurableMessage.create("upms", "workflow", original.eventId(), "OtherType", 1, original.payloadJson()),
                DurableMessage.create("upms", "workflow", original.eventId(), original.type(), 2, original.payloadJson()));
        for (var message : rejected) {
            assertThatThrownBy(() -> executor.receive(message)).isInstanceOf(InboxDeliveryException.class)
                    .satisfies(ex -> assertThat(((InboxDeliveryException) ex).kind()).isEqualTo(InboxDeliveryException.Kind.PERMANENT));
        }
        assertThatThrownBy(() -> new DurableMessage("upms", "workflow", original.eventId(), original.type(), 1, "{}", original.payloadHash())).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("reliable_inbox")).isOne();
        assertThat(count("inbox_test_business")).isOne();
    }

    @Test
    void expiredAndReplacedTokensCannotEnterTheHandlerOrOverwriteTheNewOwner() {
        var event = message("upms", 1); inbox.accept(event);
        var old = inbox.claim("workflow", 1).get(0);
        var calls = new AtomicInteger();
        var executor = executor(message -> { calls.incrementAndGet(); return effect(message); });
        assertThatThrownBy(() -> executor.receive(event)).isInstanceOf(InboxDeliveryException.class);
        inboxExpired();
        var current = inbox.claim("workflow", 1).get(0);
        assertThatThrownBy(() -> executor.execute(old)).isInstanceOf(InboxDeliveryException.class);
        assertThat(inbox.markFailed(old, new IllegalStateException())).isFalse();
        assertThat(calls).hasValue(0);
        assertThat(inboxState(event)).isEqualTo("IN_FLIGHT");
        assertThat(executor.execute(current)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
        assertThatThrownBy(() -> executor.execute(old)).isInstanceOf(InboxDeliveryException.class);
        assertThat(inbox.markFailed(old, new IllegalStateException())).isFalse();
        assertThat(calls).hasValue(1);
        var expiredEvent = message("upms", 2); inbox.accept(expiredEvent);
        var expired = inbox.claim("workflow", 1).get(0); inboxExpired();
        assertThatThrownBy(() -> executor.execute(expired)).isInstanceOf(InboxDeliveryException.class);
        assertThat(calls).hasValue(1);
        assertThat(inboxState(expiredEvent)).isNotIn("PROCESSED", "IGNORED");
    }

    @Test
    void restartAfterTakeoverOrClaimRecoversOnlyDatabaseState() {
        var event = message("upms", 1); inbox.accept(event);
        var restartedStore = new JdbcInboxStore(dataSource, manager, properties);
        assertThat(executor(restartedStore, "workflow", this::effect).recoverOnce(20)).isOne();
        var claimedEvent = message("upms", 2); inbox.accept(claimedEvent);
        inbox.claim("workflow", 1); inboxExpired();
        assertThat(executor(new JdbcInboxStore(dataSource, manager, properties), "workflow", this::effect).recoverOnce(20)).isOne();
        assertThat(count("inbox_test_business")).isEqualTo(2);
        assertThat(count("reliable_outbox")).isEqualTo(2);
        assertThat(inbox.find("workflow", claimedEvent.eventId()).message()).isEqualTo(claimedEvent);
    }

    @Test
    void oldRepeatableReadSnapshotCannotHideConcurrentTakeoverAndResourcesAreRestored() throws Exception {
        var event = message("upms", 1);
        var threads = Executors.newSingleThreadExecutor();
        try {
            transaction.executeWithoutResult(status -> {
                assertThat(count("reliable_inbox")).isZero();
                Object originalResource = TransactionSynchronizationManager.getResource(dataSource);
                try { threads.submit(() -> inbox.accept(event)).get(5, TimeUnit.SECONDS); }
                catch (Exception ex) { throw new AssertionError(ex); }
                assertThat(inbox.accept(event).message()).isEqualTo(event);
                assertThat(TransactionSynchronizationManager.getResource(dataSource)).isSameAs(originalResource);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                status.setRollbackOnly();
            });
        }
        finally { threads.shutdownNow(); }
        assertThat(count("reliable_inbox")).isOne();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
        assertThat(executor(this::effect).receive(event)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
    }

    @Test
    void targetsWithTheSameEventIdAreIsolatedAndSourcesWithinATargetCannotSpoofDuplicates() {
        var first = message("upms", 1);
        var second = DurableMessage.create("upms", "other", first.eventId(), first.type(), 1, first.payloadJson());
        executor(this::effect).receive(first);
        executor(inbox, "other", this::effect).receive(second);
        assertThat(inbox.find("workflow", first.eventId()).message()).isEqualTo(first);
        assertThat(inbox.find("other", first.eventId()).message()).isEqualTo(second);
        assertThat(count("inbox_test_business")).isEqualTo(2);
        assertThat(count("reliable_outbox")).isEqualTo(2);
        var pending = DurableMessage.create("upms", "other", UUID.randomUUID().toString(), first.type(), 1, first.payloadJson());
        inbox.accept(pending);
        assertThat(inbox.claim("workflow", 20)).isEmpty();
        assertThat(inbox.claim("other", 20)).singleElement().satisfies(lease -> assertThat(lease.message()).isEqualTo(pending));
    }
    @Test
    void anExecutorCannotFailAnotherTargetsLease() {
        var event = DurableMessage.create("upms", "other", UUID.randomUUID().toString(), "StartRequested", 1, "{}");
        inbox.accept(event);
        var otherLease = inbox.claim("other", 1).get(0);
        assertThatThrownBy(() -> executor(this::effect).execute(otherLease)).isInstanceOf(InboxDeliveryException.class);
        assertThat(inboxState(event)).isEqualTo("IN_FLIGHT");
        assertThat(jdbc.queryForObject("SELECT lease_token FROM reliable_inbox", String.class)).isEqualTo(otherLease.leaseToken());
        assertThat(executor(inbox, "other", this::effect).execute(otherLease)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
    }

    @Test
    void leaseValidityUsesFreshDatabaseTimeAfterWaitingToAcquireTheProcessingLock() throws Exception {
        var event = message("upms", 1); inbox.accept(event);
        var lease = inbox.claim("workflow", 1).get(0);
        var calls = new AtomicInteger();
        var current = executor(message -> { calls.incrementAndGet(); return effect(message); });
        var thread = Executors.newSingleThreadExecutor();
        try (var blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var lock = blocker.prepareStatement("SELECT event_id FROM reliable_inbox WHERE target_owner='workflow' AND event_id=? FOR UPDATE")) {
                lock.setString(1, event.eventId()); lock.executeQuery().close();
                var waiting = thread.submit(() -> current.execute(lease));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class) == 0
                        && System.nanoTime() < deadline) { Thread.onSpinWait(); }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class)).isPositive();
                // This timestamp is newer than the waiting SELECT's statement-start UTC time,
                // but already expired by the time that SELECT can acquire this row's lock.
                try (var expire = blocker.prepareStatement("UPDATE reliable_inbox SET lease_until=UTC_TIMESTAMP(6) WHERE target_owner='workflow' AND event_id=?")) {
                    expire.setString(1, event.eventId()); expire.executeUpdate();
                }
                blocker.commit();
                assertThatThrownBy(() -> waiting.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(InboxDeliveryException.class);
                assertThat(calls).hasValue(0);
                assertThat(count("inbox_test_business")).isZero();
            }
            finally { blocker.rollback(); }
        }
        finally { thread.shutdownNow(); }
    }

    @Test
    void failedTerminalInboxWriteRollsBackAlreadyExecutedBusinessAndOutboxWrites() {
        var event = message("upms", 1);
        jdbc.execute("""
                CREATE TRIGGER inbox_test_fail_terminal BEFORE UPDATE ON reliable_inbox FOR EACH ROW
                BEGIN
                    IF NEW.status = 'PROCESSED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'terminal marker unavailable';
                    END IF;
                END
                """);
        try {
            assertThatThrownBy(() -> executor(this::effect).receive(event)).isInstanceOf(InboxDeliveryException.class);
            assertThat(inboxState(event)).isEqualTo("RECEIVED");
            assertThat(count("inbox_test_business")).isZero();
            assertThat(count("reliable_outbox")).isZero();
        }
        finally { jdbc.execute("DROP TRIGGER inbox_test_fail_terminal"); }
        inboxDue();
        assertThat(executor(this::effect).receive(event)).isEqualTo(DurableMessageHandler.Result.PROCESSED);
        assertThat(count("inbox_test_business")).isOne();
        assertThat(count("reliable_outbox")).isOne();
    }

    @Test
    void registeredTypeOrSchemaChangesStillConflictWithAnExistingIdentity() {
        var event = message("upms", 1);
        var registered = new InboxExecutor(inbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "StartRequested", 1), this::effect,
                new InboxExecutor.Route("upms", "OtherType", 1), this::effect,
                new InboxExecutor.Route("upms", "StartRequested", 2), this::effect));
        registered.receive(event);
        for (var changed : List.of(
                DurableMessage.create("upms", "workflow", event.eventId(), "OtherType", 1, event.payloadJson()),
                DurableMessage.create("upms", "workflow", event.eventId(), event.type(), 2, event.payloadJson()))) {
            assertThatThrownBy(() -> registered.receive(changed)).isInstanceOf(InboxDeliveryException.class)
                    .satisfies(ex -> assertThat(((InboxDeliveryException) ex).kind()).isEqualTo(InboxDeliveryException.Kind.CONFLICT));
        }
        assertThat(inbox.find("workflow", event.eventId()).message()).isEqualTo(event);
        assertThat(count("inbox_test_business")).isOne();
    }

    @Test
    void supportedCanonicalJsonRangePersistsUnchangedInTheInbox() {
        for (String payload : List.of("{\"number\":1e400}", "[".repeat(110) + "1" + "]".repeat(110))) {
            var event = DurableMessage.create("upms", "workflow", UUID.randomUUID().toString(), "StartRequested", 1, payload);
            assertThat(executor(message -> DurableMessageHandler.Result.IGNORED).receive(event)).isEqualTo(DurableMessageHandler.Result.IGNORED);
            assertThat(inbox.find("workflow", event.eventId()).message()).isEqualTo(event);
        }
    }

}
