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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real-broker coverage for confirm, routing and inbox duplicate handling. */
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class RabbitOwnerEndpointIntegrationTest extends MysqlInboxTestSupport {
    private static final String TEST_SCHEMA = "20260921_reliable_quarantine.sql";

    @BeforeEach
    void prepareQuarantine() {
        Path schema = Path.of(System.getProperty("outbox.test.schema",
                "../../bixi-project-documents/sql/migrations/20260921_reliable_outbox.sql"));
        new ResourceDatabasePopulator(new FileSystemResource(schema.resolveSibling(TEST_SCHEMA)))
                .execute(dataSource);
        jdbc.execute("TRUNCATE TABLE reliable_quarantine");
    }

    @Test
    void confirmedDeliveryIsRoutedToPeerInboxAndDuplicateIsProcessedOnce() throws Exception {
        String host = System.getenv("RELIABLE_RABBIT_TEST_HOST");
        String portText = System.getenv("RELIABLE_RABBIT_TEST_PORT");
        assumeTrue(host != null && !host.isBlank() && portText != null && !portText.isBlank());
        int port = Integer.parseInt(portText);
        var settings = new ReliableRabbitProperties.Settings(host, port,
                env("RELIABLE_RABBIT_TEST_USERNAME", "test"),
                env("RELIABLE_RABBIT_TEST_PASSWORD", "test"), "/",
                5_000, 5_000, 5_000, 30, 4, 20);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String workflowExchange = "bixi.workflow.test." + suffix;
        String upmsExchange = "bixi.upms.test." + suffix;
        String workflowQueue = "bixi.workflow.inbox.test." + suffix;
        String upmsQueue = "bixi.upms.inbox.test." + suffix;
        var delivery = new ReliableDeliveryProperties(Duration.ofMillis(100), Duration.ofSeconds(30),
                Duration.ofSeconds(5), 20, 12);
        var workflowInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var upmsInbox = new JdbcInboxStore(dataSource, manager, delivery);
        AtomicInteger received = new AtomicInteger();
        var workflowExecutor = new InboxExecutor(workflowInbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "Started", 1), message -> DurableMessageHandler.Result.PROCESSED));
        var upmsExecutor = new InboxExecutor(upmsInbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> {
                    received.incrementAndGet();
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        var workflowEndpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "upms.workflow"),
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                workflowQueue, workflowExecutor, workflowInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        var upmsEndpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "upms.workflow"),
                upmsQueue, upmsExecutor, upmsInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        try {
            workflowEndpoint.start();
            upmsEndpoint.start();
            DurableMessage event = DurableMessage.create("workflow", "upms", UUID.randomUUID().toString(),
                    "StartRequested", 1, "{\"value\":1}");
            workflowEndpoint.deliver(event);
            awaitReceived(received, 1);
            workflowEndpoint.deliver(event);
            Thread.sleep(250);
            assertThat(received).hasValue(1);
            assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner=? AND event_id=?",
                    String.class, event.targetOwner(), event.eventId())).isEqualTo("PROCESSED");
        }
        finally {
            upmsEndpoint.stop();
            workflowEndpoint.stop();
        }
    }

    @Test
    void mandatoryReturnRejectsAnUnroutableDurableMessage() {
        String host = System.getenv("RELIABLE_RABBIT_TEST_HOST");
        String portText = System.getenv("RELIABLE_RABBIT_TEST_PORT");
        assumeTrue(host != null && !host.isBlank() && portText != null && !portText.isBlank());
        var settings = new ReliableRabbitProperties.Settings(host, Integer.parseInt(portText),
                env("RELIABLE_RABBIT_TEST_USERNAME", "test"),
                env("RELIABLE_RABBIT_TEST_PASSWORD", "test"), "/",
                5_000, 5_000, 5_000, 30, 4, 20);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String workflowExchange = "bixi.workflow.test." + suffix;
        String upmsExchange = "bixi.upms.test." + suffix;
        var delivery = ReliableDeliveryProperties.defaults();
        var workflowInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var upmsInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var workflowExecutor = new InboxExecutor(workflowInbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "Started", 1), message -> DurableMessageHandler.Result.PROCESSED));
        var upmsExecutor = new InboxExecutor(upmsInbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> DurableMessageHandler.Result.PROCESSED));
        var workflowEndpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "missing.route"),
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                "bixi.workflow.inbox.test." + suffix, workflowExecutor, workflowInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        var upmsEndpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "upms.workflow"),
                "bixi.upms.inbox.test." + suffix, upmsExecutor, upmsInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        try {
            workflowEndpoint.start();
            upmsEndpoint.start();
            DurableMessage event = DurableMessage.create("workflow", "upms", UUID.randomUUID().toString(),
                    "StartRequested", 1, "{\"value\":2}");
            assertThatThrownBy(() -> workflowEndpoint.deliver(event))
                    .isInstanceOf(org.springframework.amqp.AmqpException.class);
        }
        finally {
            upmsEndpoint.stop();
            workflowEndpoint.stop();
        }
    }

    @Test
    void malformedWireIsQuarantinedBeforeManualAck() throws Exception {
        String host = System.getenv("RELIABLE_RABBIT_TEST_HOST");
        String portText = System.getenv("RELIABLE_RABBIT_TEST_PORT");
        assumeTrue(host != null && !host.isBlank() && portText != null && !portText.isBlank());
        int port = Integer.parseInt(portText);
        var settings = new ReliableRabbitProperties.Settings(host, port,
                env("RELIABLE_RABBIT_TEST_USERNAME", "test"),
                env("RELIABLE_RABBIT_TEST_PASSWORD", "test"), "/",
                5_000, 5_000, 5_000, 30, 4, 20);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String workflowExchange = "bixi.workflow.test." + suffix;
        String upmsExchange = "bixi.upms.test." + suffix;
        String queue = "bixi.upms.inbox.test." + suffix;
        var delivery = ReliableDeliveryProperties.defaults();
        var upmsInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var upmsExecutor = new InboxExecutor(upmsInbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> DurableMessageHandler.Result.PROCESSED));
        var endpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "upms.workflow"),
                queue, upmsExecutor, upmsInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        String evidenceId = "bad-wire-" + suffix;
        try {
            endpoint.start();
            var factory = new com.rabbitmq.client.ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(settings.username());
            factory.setPassword(settings.password());
            try (var connection = factory.newConnection(); var channel = connection.createChannel()) {
                var properties = new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                        .messageId(evidenceId).deliveryMode(2).build();
                channel.basicPublish(workflowExchange, "upms.workflow", true, properties,
                        "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            awaitQuarantine(evidenceId);
            assertThat(jdbc.queryForObject("SELECT reason FROM reliable_quarantine WHERE evidence_id=?",
                    String.class, evidenceId)).isEqualTo("WIRE_FORMAT_INVALID");
        }
        finally {
            endpoint.stop();
        }
    }

    @Test
    void durableMessageWaitsInQueueAcrossConsumerRestart() throws Exception {
        String host = System.getenv("RELIABLE_RABBIT_TEST_HOST");
        String portText = System.getenv("RELIABLE_RABBIT_TEST_PORT");
        assumeTrue(host != null && !host.isBlank() && portText != null && !portText.isBlank());
        int port = Integer.parseInt(portText);
        var settings = new ReliableRabbitProperties.Settings(host, port,
                env("RELIABLE_RABBIT_TEST_USERNAME", "test"),
                env("RELIABLE_RABBIT_TEST_PASSWORD", "test"), "/",
                5_000, 5_000, 5_000, 30, 4, 20);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String workflowExchange = "bixi.workflow.test." + suffix;
        String upmsExchange = "bixi.upms.test." + suffix;
        String workflowQueue = "bixi.workflow.inbox.test." + suffix;
        String upmsQueue = "bixi.upms.inbox.test." + suffix;
        var delivery = ReliableDeliveryProperties.defaults();
        var workflowInbox = new JdbcInboxStore(dataSource, manager, delivery);
        var upmsInbox = new JdbcInboxStore(dataSource, manager, delivery);
        AtomicInteger received = new AtomicInteger();
        var workflowExecutor = new InboxExecutor(workflowInbox, "workflow", Map.of(
                new InboxExecutor.Route("upms", "Started", 1), message -> DurableMessageHandler.Result.PROCESSED));
        var upmsExecutor = new InboxExecutor(upmsInbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> {
                    received.incrementAndGet();
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        var workflowEndpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "upms.workflow"),
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                workflowQueue, workflowExecutor, workflowInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        var upmsEndpoint = new RabbitOwnerEndpoint(settings,
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, "workflow.upms"),
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, "upms.workflow"),
                upmsQueue, upmsExecutor, upmsInbox,
                new JdbcQuarantineStore(dataSource, manager), delivery, () -> true);
        try {
            workflowEndpoint.start();
            upmsEndpoint.start();
            upmsEndpoint.stop();
            DurableMessage event = DurableMessage.create("workflow", "upms", UUID.randomUUID().toString(),
                    "StartRequested", 1, "{\"value\":3}");
            workflowEndpoint.deliver(event);
            upmsEndpoint.start();
            awaitProcessed(received, event);
        }
        finally {
            upmsEndpoint.stop();
            workflowEndpoint.stop();
        }
    }

    private static void awaitReceived(AtomicInteger received, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (received.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(received).hasValue(expected);
    }

    private void awaitProcessed(AtomicInteger received, DurableMessage event) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while ((!processed(event) || received.get() < 1)
                && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(received).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner=? AND event_id=?",
                String.class, event.targetOwner(), event.eventId())).isEqualTo("PROCESSED");
    }

    private boolean processed(DurableMessage event) {
        return jdbc.query("SELECT status FROM reliable_inbox WHERE target_owner=? AND event_id=?",
                (row, index) -> row.getString("status"), event.targetOwner(), event.eventId()).stream()
                .anyMatch("PROCESSED"::equals);
    }

    private void awaitQuarantine(String evidenceId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM reliable_quarantine WHERE evidence_id=?",
                    Integer.class, evidenceId);
            if (count != null && count == 1) return;
            Thread.sleep(50);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_quarantine WHERE evidence_id=?",
                Integer.class, evidenceId)).isOne();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

}
