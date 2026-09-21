package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class JdbcInboxStoreTest extends MysqlInboxTestSupport {
    @Test
    void claimsRespectCapacityAndSkipAnotherWorkersHeldCommit() throws Exception {
        for (int i = 0; i < 20; i++) inbox.accept(message("upms", i));
        var holds = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pausingManager = new DataSourceTransactionManager(dataSource) {
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                holds.countDown(); await(release); super.doCommit(status);
            }
        };
        var firstStore = new JdbcInboxStore(dataSource, pausingManager, properties);
        var thread = Executors.newSingleThreadExecutor();
        try {
            var first = thread.submit(() -> firstStore.claim("workflow", 10));
            assertThat(holds.await(5, TimeUnit.SECONDS)).isTrue();
            var second = inbox.claim("workflow", 10);
            assertThat(second).hasSize(10); release.countDown();
            var ids = new HashSet<String>();
            first.get(5, TimeUnit.SECONDS).forEach(lease -> ids.add(lease.message().eventId()));
            second.forEach(lease -> ids.add(lease.message().eventId()));
            assertThat(ids).hasSize(20);
            assertThat(inbox.claim("workflow", 20)).isEmpty();
        }
        finally { release.countDown(); thread.shutdownNow(); }
        assertThatThrownBy(() -> inbox.claim("workflow", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inbox.claim("workflow", 21)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retriesBackoffAndTwelveAttemptLimitSurviveReconstruction() {
        var event = message("upms", 1);
        int[] seconds = {1, 2, 4, 8, 16, 32, 60, 120, 240, 300, 300, 300};
        for (int attempt = 1; attempt <= 12; attempt++) {
            var current = executor(new JdbcInboxStore(dataSource, manager, properties), "workflow", message -> {
                assertThat(message).isEqualTo(event); throw new IllegalStateException("sensitive " + "x".repeat(2000));
            });
            assertThatThrownBy(() -> current.receive(event)).isInstanceOf(InboxDeliveryException.class);
            assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_inbox", Integer.class)).isEqualTo(attempt);
            assertThat(jdbc.queryForObject("SELECT last_error FROM reliable_inbox", String.class)).isEqualTo("java.lang.IllegalStateException");
            assertThat(inbox.claim("workflow", 1)).isEmpty();
            if (attempt < 12) {
                assertThat(inboxState(event)).isEqualTo("RECEIVED");
                long micros = jdbc.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND, UTC_TIMESTAMP(6), next_attempt_at) FROM reliable_inbox", Long.class);
                assertThat(micros).isBetween(seconds[attempt - 1] * 1_000_000L - 750_000L, seconds[attempt - 1] * 1_200_000L);
                inboxDue();
            }
        }
        assertThat(inboxState(event)).isEqualTo("FAILED");
        assertThatThrownBy(() -> executor(this::effect).receive(event)).isInstanceOf(InboxDeliveryException.class);
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_inbox", Integer.class)).isEqualTo(12);
        assertThat(count("inbox_test_business")).isZero();
    }

    @Test
    void dyingOnTheLastClaimBecomesFailedWithoutAThirteenthHandlerCall() {
        var event = message("upms", 1); inbox.accept(event);
        jdbc.update("UPDATE reliable_inbox SET attempts=11");
        assertThat(inbox.claim("workflow", 1).get(0).attempt()).isEqualTo(12);
        inboxExpired();
        assertThat(executor(this::effect).recoverOnce(20)).isZero();
        assertThat(inboxState(event)).isEqualTo("FAILED");
        assertThat(count("inbox_test_business")).isZero();
    }

    @Test
    void sessionTimezoneDoesNotMoveDatabaseUtcLeaseOrRetryBoundaries() {
        var shifted = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL").replace("connectionTimeZone=UTC", "connectionTimeZone=%2B08:00"), "root", System.getenv("MYSQL_ROOT_PASSWORD"));
        var shiftedJdbc = new JdbcTemplate(shifted);
        var shiftedStore = new JdbcInboxStore(shifted, new DataSourceTransactionManager(shifted), properties);
        assertThat(shiftedJdbc.queryForObject("SELECT TIMESTAMPDIFF(HOUR,UTC_TIMESTAMP(6),NOW(6))", Integer.class)).isEqualTo(8);
        var event = message("upms", 1); shiftedStore.accept(event);
        assertThat(shiftedJdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,received_at,UTC_TIMESTAMP(6))) FROM reliable_inbox", Long.class)).isLessThan(2);
        var lease = shiftedStore.claim("workflow", 1).get(0);
        assertThat(shiftedJdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(6),lease_until) FROM reliable_inbox", Long.class)).isBetween(28L,30L);
        assertThat(shiftedStore.claim("workflow", 1)).isEmpty();
        assertThat(shiftedStore.markFailed(lease, new IllegalStateException())).isTrue();
        assertThat(shiftedStore.claim("workflow", 1)).isEmpty();
        inboxDue();
        var reclaimed = shiftedStore.claim("workflow", 1).get(0);
        assertThat(executor(shiftedStore, "workflow", message -> DurableMessageHandler.Result.IGNORED).execute(reclaimed)).isEqualTo(DurableMessageHandler.Result.IGNORED);
        assertThat(shiftedJdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,processed_at,UTC_TIMESTAMP(6))) FROM reliable_inbox", Long.class)).isLessThan(2);
    }

    @Test
    void failureEvidenceWriteFailureCannotBeMistakenForDurableRetryTakeover() {
        var event = message("upms", 1);
        jdbc.execute("""
                CREATE TRIGGER inbox_test_fail_error_record BEFORE UPDATE ON reliable_inbox FOR EACH ROW
                BEGIN
                    IF OLD.status = 'IN_FLIGHT' AND NEW.status = 'RECEIVED' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'cannot save retry evidence';
                    END IF;
                END
                """);
        try {
            assertThatThrownBy(() -> executor(message -> { effect(message); throw new IllegalStateException(); }).receive(event))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(inboxState(event)).isEqualTo("IN_FLIGHT");
            assertThat(count("inbox_test_business")).isZero();
            assertThat(count("reliable_outbox")).isZero();
        }
        finally { jdbc.execute("DROP TRIGGER inbox_test_fail_error_record"); }
        inboxExpired();
        assertThat(executor(this::effect).recoverOnce(1)).isOne();
    }
    @Test
    void recoveryAdvancesPastAnExpiredFinalAttemptToOtherDueMessagesWithinTheSameBudget() {
        var exhausted = message("upms", 1); inbox.accept(exhausted);
        jdbc.update("UPDATE reliable_inbox SET attempts=11");
        assertThat(inbox.claim("workflow", exhausted.eventId()).attempt()).isEqualTo(12);
        inboxExpired();
        var due = message("upms", 2); inbox.accept(due);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThat(executor(message -> {
            calls.incrementAndGet(); assertThat(message).isEqualTo(due); return effect(message);
        }).recoverOnce(20)).isOne();
        assertThat(calls).hasValue(1);
        assertThat(inboxState(exhausted)).isEqualTo("FAILED");
        assertThat(inboxState(due)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_inbox WHERE event_id=?", Integer.class, exhausted.eventId())).isEqualTo(12);
        assertThat(count("inbox_test_business")).isOne();
    }

    @Test
    void expiredFinalAttemptCleanupConsumesABoundedScanBudgetWithoutReservingThePendingBatch() {
        for (int i = 0; i < 25; i++) inbox.accept(message("upms", i));
        jdbc.update("UPDATE reliable_inbox SET attempts=12,status='IN_FLIGHT',lease_token=UUID(),lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6))");
        var pending = message("upms", 100); inbox.accept(pending);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var worker = executor(message -> {
            calls.incrementAndGet(); assertThat(message).isEqualTo(pending); return effect(message);
        });
        assertThat(worker.recoverOnce(20)).isZero();
        assertThat(calls).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_inbox WHERE status='FAILED'", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_inbox WHERE status='IN_FLIGHT'", Integer.class)).isEqualTo(5);
        assertThat(inboxState(pending)).isEqualTo("RECEIVED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_inbox WHERE event_id=?", Integer.class, pending.eventId())).isZero();
        assertThat(worker.recoverOnce(20)).isOne();
        assertThat(calls).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_inbox WHERE status='FAILED'", Integer.class)).isEqualTo(25);
        assertThat(inboxState(pending)).isEqualTo("PROCESSED");
    }

}
