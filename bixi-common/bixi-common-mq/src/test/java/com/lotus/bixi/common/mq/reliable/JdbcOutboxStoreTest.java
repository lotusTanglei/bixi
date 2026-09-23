package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class JdbcOutboxStoreTest extends MysqlOutboxTestSupport {
    @Test
    void businessRollbackAlsoRemovesOutboxAndEnqueueRequiresTheExistingTransaction() {
        var event = message("upms", 1);
        assertThatThrownBy(() -> store.enqueue(event, "key", null, null)).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO outbox_test_business VALUES (1)");
            store.enqueue(event, "key", null, null);
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("reliable_outbox")).isZero();
        assertThat(count("outbox_test_business")).isZero();
        enqueue(event, "key");
        assertThat(count("reliable_outbox")).isOne();
    }

    @Test
    void identicalDuplicateDoesNotResetDeliveredStateOrOriginalPayload() {
        var event = message("upms", 1);
        enqueue(event, "key");
        var lease = store.claim("upms", 1).get(0);
        assertThat(store.markDelivered(lease)).isTrue();
        enqueue(event, "key");
        assertThat(count("reliable_outbox")).isOne();
        assertThat(state(event)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT payload_json FROM reliable_outbox", String.class)).isEqualTo(event.payloadJson());
        assertThat(store.claim("upms", 1)).isEmpty();
    }

    @Test
    void identityOrDedupContentConflictRollsBackBusinessEvenWhenCallerCatchesIt() {
        var first = message("upms", 1);
        enqueue(first, "key");
        var changed = DurableMessage.create("upms", "workflow", first.eventId(), "StartRequested", 1, "{\"value\":2}");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO outbox_test_business VALUES (1)");
            try {
                store.enqueue(changed, "key", null, null);
            }
            catch (OutboxConflictException expected) {
                // Joining the transaction must still make this caller's work rollback-only.
            }
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(count("outbox_test_business")).isZero();
        assertThatThrownBy(() -> enqueue(message("upms", 1), "key")).isInstanceOf(OutboxConflictException.class);
        assertThatThrownBy(() -> enqueue(first, "other-key")).isInstanceOf(OutboxConflictException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> store.enqueue(first, "key", "aggregate", 1L)))
                .isInstanceOf(OutboxConflictException.class);
        assertThat(count("reliable_outbox")).isOne();
        assertThat(jdbc.queryForObject("SELECT payload_json FROM reliable_outbox", String.class)).isEqualTo(first.payloadJson());
    }

    @Test
    void repeatableReadSnapshotBeforeConcurrentInsertDoesNotHideTheDuplicate() throws Exception {
        var event = message("upms", 1);
        var snapshotReady = new CountDownLatch(1);
        var insertCommitted = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var existingSnapshot = executor.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(count("reliable_outbox")).isZero();
                snapshotReady.countDown();
                await(insertCommitted);
                store.enqueue(event, "key", null, null);
            }));
            assertThat(snapshotReady.await(5, TimeUnit.SECONDS)).isTrue();
            enqueue(event, "key");
            insertCommitted.countDown();
            existingSnapshot.get(10, TimeUnit.SECONDS);
            assertThat(count("reliable_outbox")).isOne();
        }
        finally {
            insertCommitted.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentIdenticalWritersConvergeOnOneOutboxRow() throws Exception {
        var event = message("upms", 1);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(executor.submit(() -> {
                ready.countDown(); await(go); enqueue(event, "key");
            }), executor.submit(() -> {
                ready.countDown(); await(go); enqueue(event, "key");
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
            assertThat(count("reliable_outbox")).isOne();
        }
        finally { go.countDown(); executor.shutdownNow(); }
    }

    @Test
    void concurrentDistinctWritersDoNotDeadlock() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 32; round++) {
                var first = message("upms", round * 2);
                var second = message("upms", round * 2 + 1);
                var ready = new CountDownLatch(2);
                var go = new CountDownLatch(1);
                var futures = List.of(executor.submit(() -> {
                    ready.countDown();
                    await(go);
                    enqueue(first, "distinct-" + first.eventId());
                }), executor.submit(() -> {
                    ready.countDown();
                    await(go);
                    enqueue(second, "distinct-" + second.eventId());
                }));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                for (var future : futures) future.get(10, TimeUnit.SECONDS);
            }
            assertThat(count("reliable_outbox")).isEqualTo(64);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void independentClaimersReceiveDisjointLeasesAndSkipAnActuallyLockedRow() throws Exception {
        for (int i = 0; i < 20; i++) enqueue(message("upms", i), "key-" + i);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { ready.countDown(); await(go); return store.claim("upms", 10); });
            var secondStore = new JdbcOutboxStore(dataSource, manager, properties);
            var second = executor.submit(() -> { ready.countDown(); await(go); return secondStore.claim("upms", 10); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            var firstLeases = first.get(10, TimeUnit.SECONDS);
            var secondLeases = second.get(10, TimeUnit.SECONDS);
            assertThat(firstLeases).hasSize(10); assertThat(secondLeases).hasSize(10);
            var ids = new HashSet<String>();
            firstLeases.forEach(lease -> ids.add(lease.message().eventId()));
            secondLeases.forEach(lease -> ids.add(lease.message().eventId()));
            assertThat(ids).hasSize(20);
            assertThat(store.claim("upms", 20)).isEmpty();
        }
        finally { go.countDown(); executor.shutdownNow(); }
        jdbc.execute("TRUNCATE TABLE reliable_outbox");
        var locked = message("upms", 1); enqueue(locked, "locked"); enqueue(message("upms", 2), "free");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT event_id FROM reliable_outbox WHERE source_owner='upms' AND event_id=? FOR UPDATE")) {
                statement.setString(1, locked.eventId()); statement.executeQuery().close();
                assertThat(store.claim("upms", 20)).singleElement().satisfies(lease ->
                        assertThat(lease.message().eventId()).isNotEqualTo(locked.eventId()));
            }
            finally { connection.rollback(); }
        }
    }

    @Test
    void aClaimerHoldingItsCommitDoesNotLockTheRestOfTheBatch() throws Exception {
        for (int i = 0; i < 20; i++) enqueue(message("upms", i), "key-" + i);
        var holdsClaim = new CountDownLatch(1);
        var releaseCommit = new CountDownLatch(1);
        var pausingManager = new DataSourceTransactionManager(dataSource) {
            @Override
            protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                holdsClaim.countDown();
                await(releaseCommit);
                super.doCommit(status);
            }
        };
        var firstStore = new JdbcOutboxStore(dataSource, pausingManager, properties);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> firstStore.claim("upms", 10));
            assertThat(holdsClaim.await(5, TimeUnit.SECONDS)).isTrue();
            var second = store.claim("upms", 10);
            assertThat(second).hasSize(10);
            releaseCommit.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).hasSize(10)
                    .doesNotContainAnyElementsOf(second);
        }
        finally { releaseCommit.countDown(); executor.shutdownNow(); }
    }

    @Test
    void expiredLeaseIsReclaimedAndOldWorkersCannotCompleteOrFailIt() {
        var event = message("upms", 1); enqueue(event, "key");
        var stale = store.claim("upms", 1).get(0);
        assertThat(store.claim("upms", 1)).isEmpty();
        expireLeases();
        var current = store.claim("upms", 1).get(0);
        assertThat(current.leaseToken()).isNotEqualTo(stale.leaseToken());
        assertThat(current.attempt()).isEqualTo(2);
        assertThat(store.markDelivered(stale)).isFalse();
        assertThat(store.markFailed(stale, new RuntimeException("secret=payload"))).isFalse();
        assertThat(state(event)).isEqualTo("IN_FLIGHT");
        assertThat(store.markDelivered(current)).isTrue();
        assertThat(store.markFailed(current, new RuntimeException())).isFalse();
        assertThat(state(event)).isEqualTo("DELIVERED");
    }

    @Test
    void retryBackoffAndAttemptLimitSurviveStoreReconstruction() {
        var event = message("upms", 1); enqueue(event, "key");
        int[] seconds = {1, 2, 4, 8, 16, 32, 60, 120, 240, 300, 300, 300};
        for (int i = 1; i <= 12; i++) {
            store = new JdbcOutboxStore(dataSource, manager, properties);
            var lease = store.claim("upms", 1).get(0);
            assertThat(lease.attempt()).isEqualTo(i);
            assertThat(lease.message()).isEqualTo(event);
            assertThat(store.markFailed(lease, new RuntimeException("credential=do-not-store:" + "payload".repeat(1000)))).isTrue();
            assertThat(store.claim("upms", 1)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT last_error FROM reliable_outbox", String.class)).isEqualTo("java.lang.RuntimeException");
            if (i < 12) {
                assertThat(state(event)).isEqualTo("PENDING");
                long micros = jdbc.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND, UTC_TIMESTAMP(6), next_attempt_at) FROM reliable_outbox", Long.class);
                assertThat(micros).isBetween(seconds[i - 1] * 1_000_000L - 500_000L, (long) (seconds[i - 1] * 1_200_000L));
                makeDue();
            }
        }
        assertThat(state(event)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox", Integer.class)).isEqualTo(12);
    }

    @Test
    void lastAttemptWorkerCrashEventuallyBecomesFailedWithoutThirteenthSend() {
        var event = message("upms", 1); enqueue(event, "key");
        jdbc.update("UPDATE reliable_outbox SET attempts=11");
        assertThat(store.claim("upms", 1).get(0).attempt()).isEqualTo(12);
        expireLeases();
        assertThat(new JdbcOutboxStore(dataSource, manager, properties).claim("upms", 1)).isEmpty();
        assertThat(state(event)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_outbox", Integer.class)).isEqualTo(12);
    }

    @Test
    void operatorRetryUsesFailedStatusCasAndListsMetadataWithoutChangingPayload() {
        var event = message("upms", 1);
        enqueue(event, "key");
        assertThat(store.retry("upms", event.eventId())).isFalse();
        jdbc.update("UPDATE reliable_outbox SET status='FAILED', attempts=12, last_error='boom'");

        var listed = store.list("upms", "FAILED", 20);
        assertThat(listed).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.message()).isEqualTo(event);
            assertThat(snapshot.status()).isEqualTo("FAILED");
            assertThat(snapshot.attempts()).isEqualTo(12);
        });
        assertThat(store.retry("upms", event.eventId())).isTrue();
        assertThat(state(event)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT last_error FROM reliable_outbox", String.class)).isNull();
        assertThat(store.retry("upms", event.eventId())).isFalse();
    }

    @Test
    void sameDatabaseKeepsOwnerIdentityClaimsAndFencesIsolated() {
        var upms = message("upms", 1);
        var other = DurableMessage.create("other", "workflow", upms.eventId(), upms.type(), 1, "{\"value\":2}");
        enqueue(upms, "key"); enqueue(other, "key");
        var upmsLease = store.claim("upms", 20).get(0);
        assertThat(upmsLease.message()).isEqualTo(upms);
        assertThat(store.markDelivered(upmsLease)).isTrue();
        assertThat(state(other)).isEqualTo("PENDING");
        assertThat(store.claim("other", 20)).singleElement().satisfies(lease -> assertThat(lease.message()).isEqualTo(other));
        assertThatThrownBy(() -> store.claim("upms", 21)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.claim("upms", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void databaseUtcControlsEligibilityAndLeaseDatesEvenWhenJdbcSessionIsEightHoursAhead() {
        var otherZoneSource = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL")
                .replace("connectionTimeZone=UTC", "connectionTimeZone=%2B08:00"), "root", System.getenv("MYSQL_ROOT_PASSWORD"));
        var otherZoneJdbc = new JdbcTemplate(otherZoneSource);
        var otherZoneManager = new DataSourceTransactionManager(otherZoneSource);
        var otherZoneStore = new JdbcOutboxStore(otherZoneSource, otherZoneManager, properties);
        assertThat(otherZoneJdbc.queryForObject("SELECT TIMESTAMPDIFF(HOUR, UTC_TIMESTAMP(6), NOW(6))", Integer.class)).isEqualTo(8);
        var event = message("upms", 1);
        new TransactionTemplate(otherZoneManager).executeWithoutResult(status -> otherZoneStore.enqueue(event, "key", null, null));
        assertThat(otherZoneJdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND, created_at, UTC_TIMESTAMP(6))) FROM reliable_outbox", Long.class)).isLessThan(2);
        var lease = otherZoneStore.claim("upms", 1).get(0);
        assertThat(otherZoneJdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), lease_until) FROM reliable_outbox", Long.class)).isBetween(28L, 30L);
        assertThat(otherZoneStore.claim("upms", 1)).isEmpty();
        expireLeases();
        var reclaimed = otherZoneStore.claim("upms", 1).get(0);
        assertThat(otherZoneStore.markDelivered(lease)).isFalse();
        assertThat(otherZoneStore.markDelivered(reclaimed)).isTrue();
        assertThat(otherZoneJdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND, delivered_at, UTC_TIMESTAMP(6))) FROM reliable_outbox", Long.class)).isLessThan(2);
    }

    @Test
    void transactionManagerMustOwnTheSameDatasource() {
        var unrelated = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root", System.getenv("MYSQL_ROOT_PASSWORD"));
        assertThatThrownBy(() -> new JdbcOutboxStore(dataSource, new DataSourceTransactionManager(unrelated), properties))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptedHighPrecisionNumbersSurviveEnqueueAndHashCheckedReadback() {
        for (String number : new String[] {"1e400", "1e-400", "123456789012345678901234567890.1234567890123456789"}) {
            var event = DurableMessage.create("upms", "workflow", UUID.randomUUID().toString(), "StartRequested", 1,
                    "{\"number\":" + number + "}");
            enqueue(event, event.eventId());
            assertThat(jdbc.queryForObject("SELECT payload_json FROM reliable_outbox WHERE event_id=?", String.class, event.eventId())).isEqualTo(event.payloadJson());
            var lease = store.claim("upms", 1).get(0);
            assertThat(lease.message()).isEqualTo(event);
            assertThat(store.markDelivered(lease)).isTrue();
        }
    }

    @Test
    void acceptedDeepJsonSurvivesEnqueueAndHashCheckedReadback() {
        String payload = "[".repeat(110) + "1" + "]".repeat(110);
        var event = DurableMessage.create("upms", "workflow", UUID.randomUUID().toString(), "StartRequested", 1, payload);
        enqueue(event, "nested");
        assertThat(jdbc.queryForObject("SELECT payload_json FROM reliable_outbox", String.class)).isEqualTo(event.payloadJson());
        var lease = store.claim("upms", 1).get(0);
        assertThat(lease.message()).isEqualTo(event);
        assertThat(store.markDelivered(lease)).isTrue();
    }

}
