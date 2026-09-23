package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Two-phase real-broker coverage; the shell script restarts Rabbit between JVMs. */
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class RabbitBrokerRestartIntegrationTest extends MysqlInboxTestSupport {
    private static final String EXCHANGE = "bixi.workflow.restart.integration";
    private static final String UPMS_EXCHANGE = "bixi.upms.restart.integration";
    private static final String WORKFLOW_QUEUE = "bixi.workflow.restart.integration";
    private static final String UPMS_QUEUE = "bixi.upms.restart.integration";
    private static final String EVENT_ID = UUID.nameUUIDFromBytes(
            "rabbit-broker-restart-event".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();

    @BeforeEach
    void prepareQuarantine() {
        Path schema = Path.of(System.getProperty("outbox.test.schema",
                "../../bixi-project-documents/sql/migrations/20260921_reliable_outbox.sql"));
        new ResourceDatabasePopulator(new FileSystemResource(schema.resolveSibling("20260921_reliable_quarantine.sql")))
                .execute(dataSource);
        jdbc.execute("TRUNCATE TABLE reliable_quarantine");
        jdbc.update("DELETE FROM reliable_inbox WHERE event_id=?", EVENT_ID);
    }

    @Test
    void seedDurableMessageBeforeExternalBrokerRestart() {
        assumeConfigured();
        var endpoints = endpoints(new AtomicInteger());
        try {
            endpoints.workflow.start();
            endpoints.upms.start();
            endpoints.upms.stop();
            endpoints.workflow.deliver(DurableMessage.create("workflow", "upms", EVENT_ID,
                    "StartRequested", 1, "{\"value\":5}"));
        }
        finally {
            endpoints.upms.stop();
            endpoints.workflow.stop();
        }
    }

    @Test
    void consumeDurableMessageAfterExternalBrokerRestart() throws Exception {
        assumeConfigured();
        AtomicInteger received = new AtomicInteger();
        Endpoints endpoints = null;
        try {
            RuntimeException lastFailure = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                endpoints = endpoints(received);
                try {
                    endpoints.upms.start();
                    lastFailure = null;
                    break;
                }
                catch (RuntimeException unavailable) {
                    lastFailure = unavailable;
                    endpoints.upms.stop();
                    Thread.sleep(500);
                }
            }
            if (lastFailure != null) throw lastFailure;
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while ((!processed() || received.get() < 1) && System.nanoTime() < deadline) Thread.sleep(50);
            assertThat(received).hasValue(1);
            assertThat(processed()).isTrue();
        }
        finally {
            if (endpoints != null) {
                endpoints.upms.stop();
                endpoints.workflow.stop();
            }
        }
    }

    private Endpoints endpoints(AtomicInteger received) {
        String host = System.getenv("RELIABLE_RABBIT_TEST_HOST");
        int port = Integer.parseInt(System.getenv("RELIABLE_RABBIT_TEST_PORT"));
        var settings = new ReliableRabbitProperties.Settings(host, port,
                env("RELIABLE_RABBIT_TEST_USERNAME", "test"),
                env("RELIABLE_RABBIT_TEST_PASSWORD", "test"), "/",
                5_000, 5_000, 5_000, 30, 4, 20);
        var delivery = ReliableDeliveryProperties.defaults();
        var workflowInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var upmsInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var workflowExecutor = new InboxExecutor(workflowInbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "Started", 1), message -> DurableMessageHandler.Result.PROCESSED));
        var upmsExecutor = new InboxExecutor(upmsInbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> {
                    received.incrementAndGet();
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        var workflow = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("workflow", "upms", EXCHANGE, "upms.workflow"),
                new RabbitDurableTransport.Route("upms", "workflow", UPMS_EXCHANGE, "workflow.upms"),
                WORKFLOW_QUEUE, workflowExecutor, workflowInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        var upms = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("upms", "workflow", UPMS_EXCHANGE, "workflow.upms"),
                new RabbitDurableTransport.Route("workflow", "upms", EXCHANGE, "upms.workflow"),
                UPMS_QUEUE, upmsExecutor, upmsInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        return new Endpoints(workflow, upms);
    }

    private static void assumeConfigured() {
        assumeTrue(System.getenv("RELIABLE_RABBIT_TEST_HOST") != null);
        assumeTrue(System.getenv("RELIABLE_RABBIT_TEST_PORT") != null);
    }

    private boolean processed() {
        return jdbc.query("SELECT status FROM reliable_inbox WHERE target_owner=? AND event_id=?",
                (row, index) -> row.getString("status"), "upms", EVENT_ID).stream()
                .anyMatch("PROCESSED"::equals);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Endpoints(RabbitOwnerEndpoint workflow, RabbitOwnerEndpoint upms) { }
}
