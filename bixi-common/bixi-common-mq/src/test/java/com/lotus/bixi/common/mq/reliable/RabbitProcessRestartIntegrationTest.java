package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Two JVM phases prove that an application crash leaves only database state to recover. */
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class RabbitProcessRestartIntegrationTest {
    private static final String EVENT_ID = UUID.nameUUIDFromBytes(
            "workflow-application-process-restart".getBytes(StandardCharsets.UTF_8)).toString();
    private DataSource dataSource;
    private DataSourceTransactionManager manager;
    private JdbcTemplate jdbc;
    private ReliableDeliveryProperties properties = new ReliableDeliveryProperties(
            Duration.ofMillis(100), Duration.ofSeconds(5), Duration.ofSeconds(1), 20, 12);

    @BeforeEach
    void prepareDatabase() {
        dataSource = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root",
                System.getenv("MYSQL_ROOT_PASSWORD"));
        manager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        Path base = schemaPath("20260921_reliable_outbox.sql");
        new ResourceDatabasePopulator(new FileSystemResource(base)).execute(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource(schemaPath("20260921_reliable_inbox.sql")))
                .execute(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS process_restart_business "
                + "(event_id CHAR(36) PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS process_restart_marker "
                + "(event_id CHAR(36) PRIMARY KEY, stage VARCHAR(32) NOT NULL) ENGINE=InnoDB");
        if (!"resume".equals(phase())) {
            jdbc.update("DELETE FROM reliable_inbox WHERE event_id=?", EVENT_ID);
            jdbc.update("DELETE FROM process_restart_business WHERE event_id=?", EVENT_ID);
            jdbc.update("DELETE FROM process_restart_marker WHERE event_id=?", EVENT_ID);
        }
    }

    @Test
    void workerProcessWaitsAfterBusinessWriteBeforeCommit() {
        assumeTrue("hold".equals(phase()));
        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, manager, properties);
        DurableMessage event = restartMessage();
        inbox.accept(event);
        InboxExecutor executor = new InboxExecutor(inbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "StartRequested", 1), message -> {
                    // The marker uses a separate auto-commit connection, so the shell can
                    // prove that this JVM reached the kill window while business work is
                    // still uncommitted in InboxExecutor's transaction.
                    markerJdbc().update("INSERT INTO process_restart_marker(event_id, stage) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE stage=VALUES(stage)", EVENT_ID, "HANDLER_ENTERED");
                    jdbc.update("INSERT INTO process_restart_business(event_id, value) VALUES (?, 1)", EVENT_ID);
                    for (;;) {
                        try {
                            Thread.sleep(1_000);
                        }
                        catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return DurableMessageHandler.Result.PROCESSED;
                        }
                    }
                }));
        executor.receive(event);
    }

    @Test
    void freshWorkerRecoversTheRolledBackBusinessTransaction() {
        assumeTrue("resume".equals(phase()));
        assertThat(jdbc.queryForObject("SELECT stage FROM process_restart_marker WHERE event_id=?",
                String.class, EVENT_ID)).isEqualTo("HANDLER_ENTERED");
        jdbc.update("UPDATE reliable_inbox SET lease_until=TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6)) "
                + "WHERE target_owner='workflow' AND event_id=?", EVENT_ID);
        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, manager, properties);
        InboxExecutor executor = new InboxExecutor(inbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "StartRequested", 1), message -> {
                    jdbc.update("INSERT INTO process_restart_business(event_id, value) VALUES (?, 1)", EVENT_ID);
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        assertThat(executor.recoverOnce(20)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM process_restart_business WHERE event_id=?",
                Integer.class, EVENT_ID)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner='workflow' AND event_id=?",
                String.class, EVENT_ID)).isEqualTo("PROCESSED");
    }

    private DurableMessage restartMessage() {
        return DurableMessage.create("upms", "workflow", EVENT_ID, "StartRequested", 1, "{\"value\":1}");
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
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path current = workingDirectory;
        for (int depth = 0; depth <= 5; depth++) {
            Path candidate = current.resolve("bixi-project-documents/sql/migrations").resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
            if (current == null) {
                break;
            }
        }
        throw new IllegalStateException("Cannot locate migration " + fileName + " from " + workingDirectory);
    }

    private static String phase() {
        return System.getenv().getOrDefault("RELIABLE_RABBIT_PROCESS_PHASE", "");
    }
}
