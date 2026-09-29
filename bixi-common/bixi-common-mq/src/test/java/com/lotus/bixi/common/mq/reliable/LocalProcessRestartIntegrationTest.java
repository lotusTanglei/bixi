package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Two JVM phases prove the single-process local transport recovers a transaction
 * that was interrupted before the Inbox commit and before the source Outbox ack.
 */
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class LocalProcessRestartIntegrationTest {
    private static final String EVENT_ID = UUID.nameUUIDFromBytes(
            "single-local-process-restart".getBytes(StandardCharsets.UTF_8)).toString();
    private static final String SOURCE = "upms";
    private static final String TARGET = "workflow";
    private static final String TYPE = "StartRequested";

    private DataSource dataSource;
    private DataSourceTransactionManager manager;
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private final ReliableDeliveryProperties properties = new ReliableDeliveryProperties(
            Duration.ofMillis(100), Duration.ofSeconds(5), Duration.ofSeconds(1), 20, 12);

    @BeforeEach
    void prepareDatabase() {
        dataSource = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root",
                System.getenv("MYSQL_ROOT_PASSWORD"));
        manager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(manager);
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource(schemaPath("20260921_reliable_outbox.sql")))
                .execute(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource(schemaPath("20260921_reliable_inbox.sql")))
                .execute(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS local_process_restart_business "
                + "(event_id CHAR(36) PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS local_process_restart_marker "
                + "(event_id CHAR(36) PRIMARY KEY, stage VARCHAR(32) NOT NULL) ENGINE=InnoDB");
        if (!"resume".equals(phase())) {
            jdbc.update("DELETE FROM reliable_outbox WHERE source_owner=? AND event_id=?", SOURCE, EVENT_ID);
            jdbc.update("DELETE FROM reliable_inbox WHERE target_owner=? AND event_id=?", TARGET, EVENT_ID);
            jdbc.update("DELETE FROM local_process_restart_business WHERE event_id=?", EVENT_ID);
            jdbc.update("DELETE FROM local_process_restart_marker WHERE event_id=?", EVENT_ID);
        }
    }

    @Test
    void localTransportRollsBackHandlerAndLeavesLeasesRecoverableWhenOwnerProcessDies() {
        assumeTrue("hold".equals(phase()));
        DurableMessage event = restartMessage();
        JdbcOutboxStore outbox = new JdbcOutboxStore(dataSource, manager, properties);
        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, manager, properties);
        transaction.executeWithoutResult(status -> outbox.enqueue(event, "local-process-restart", null, null));
        JdbcOutboxStore.Lease lease = outbox.claim(SOURCE, 1).get(0);

        InboxExecutor executor = new InboxExecutor(inbox, TARGET, Map.of(
                new InboxExecutor.Route(SOURCE, TYPE, 1), message -> {
                    markerJdbc().update("INSERT INTO local_process_restart_marker(event_id, stage) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE stage=VALUES(stage)", EVENT_ID, "HANDLER_ENTERED");
                    jdbc.update("INSERT INTO local_process_restart_business(event_id, value) VALUES (?, 1)",
                            EVENT_ID);
                    for (;;) {
                        try {
                            Thread.sleep(1_000);
                        }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return DurableMessageHandler.Result.PROCESSED;
                        }
                    }
                }));
        LocalDurableTransport local = new LocalDurableTransport(Map.of(TARGET, executor));
        // The shell kills this JVM after the independent marker is committed. The
        // handler transaction and the source markDelivered call must both be absent.
        local.deliver(lease.message());
    }

    @Test
    void newLocalOwnerReclaimsBothLeasesAndCommitsBusinessExactlyOnce() {
        assumeTrue("resume".equals(phase()));
        assertThat(jdbc.queryForObject("SELECT stage FROM local_process_restart_marker WHERE event_id=?",
                String.class, EVENT_ID)).isEqualTo("HANDLER_ENTERED");
        jdbc.update("UPDATE reliable_outbox SET lease_until=TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) "
                + "WHERE source_owner=? AND event_id=?", SOURCE, EVENT_ID);
        jdbc.update("UPDATE reliable_inbox SET lease_until=TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) "
                + "WHERE target_owner=? AND event_id=?", TARGET, EVENT_ID);

        JdbcOutboxStore outbox = new JdbcOutboxStore(dataSource, manager, properties);
        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, manager, properties);
        InboxExecutor executor = new InboxExecutor(inbox, TARGET, Map.of(
                new InboxExecutor.Route(SOURCE, TYPE, 1), message -> {
                    jdbc.update("INSERT INTO local_process_restart_business(event_id, value) VALUES (?, 1)",
                            EVENT_ID);
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        LocalDurableTransport local = new LocalDurableTransport(Map.of(TARGET, executor));
        try (OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, SOURCE, local, properties)) {
            assertThat(dispatcher.dispatchOnce()).isOne();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM local_process_restart_business WHERE event_id=?",
                Integer.class, EVENT_ID)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner=? AND event_id=?",
                String.class, TARGET, EVENT_ID)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_outbox WHERE source_owner=? AND event_id=?",
                String.class, SOURCE, EVENT_ID)).isEqualTo("DELIVERED");
    }

    private DurableMessage restartMessage() {
        return DurableMessage.create(SOURCE, TARGET, EVENT_ID, TYPE, 1, "{\"value\":1}");
    }

    private JdbcTemplate markerJdbc() {
        DriverManagerDataSource markerSource = new DriverManagerDataSource(
                System.getenv("OUTBOX_TEST_JDBC_URL"), "root", System.getenv("MYSQL_ROOT_PASSWORD"));
        return new JdbcTemplate(markerSource);
    }

    private static Path schemaPath(String fileName) {
        String configured = System.getProperty("outbox.test.schema");
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            if (Files.isRegularFile(path)) return path;
        }
        Path current = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 5 && current != null; depth++) {
            Path candidate = current.resolve("bixi-project-documents/sql/migrations").resolve(fileName);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate migration " + fileName);
    }

    private static String phase() {
        return System.getenv().getOrDefault("RELIABLE_LOCAL_PROCESS_PHASE", "");
    }
}
