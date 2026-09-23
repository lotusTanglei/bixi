package com.lotus.bixi.common.mq.reliable;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Two JVM phases prove redelivery after business commit but before Rabbit manual ack. */
@EnabledIfEnvironmentVariable(named = "OUTBOX_TEST_JDBC_URL", matches = ".+")
class RabbitAckCrashIntegrationTest {
    private static final String EXCHANGE = "bixi.ack-crash.integration";
    private static final String QUEUE = "bixi.ack-crash.integration";
    private static final String ROUTING_KEY = "workflow.upms";
    private static final String EVENT_ID = UUID.nameUUIDFromBytes(
            "rabbit-ack-crash-event".getBytes(StandardCharsets.UTF_8)).toString();

    private DataSource dataSource;
    private DataSourceTransactionManager manager;
    private JdbcTemplate jdbc;
    private final ReliableDeliveryProperties properties = new ReliableDeliveryProperties(
            Duration.ofMillis(100), Duration.ofSeconds(30), Duration.ofSeconds(2), 20, 12);

    @BeforeEach
    void prepareDatabase() {
        dataSource = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root",
                System.getenv("MYSQL_ROOT_PASSWORD"));
        manager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource(schemaPath("20260921_reliable_inbox.sql")))
                .execute(dataSource);
        new ResourceDatabasePopulator(new FileSystemResource(schemaPath("20260921_reliable_quarantine.sql")))
                .execute(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS ack_crash_business "
                + "(event_id CHAR(36) PRIMARY KEY, value INT NOT NULL) ENGINE=InnoDB");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ack_crash_marker "
                + "(event_id CHAR(36) PRIMARY KEY, stage VARCHAR(32) NOT NULL) ENGINE=InnoDB");
        if ("seed".equals(phase())) {
            jdbc.update("DELETE FROM reliable_inbox WHERE event_id=?", EVENT_ID);
            jdbc.update("DELETE FROM reliable_quarantine WHERE evidence_id=?", EVENT_ID);
            jdbc.update("DELETE FROM ack_crash_business WHERE event_id=?", EVENT_ID);
            jdbc.update("DELETE FROM ack_crash_marker WHERE event_id=?", EVENT_ID);
        }
    }

    @Test
    void seedPersistentDelivery() throws Exception {
        assumeTrue("seed".equals(phase()));
        try (var connection = rabbitFactory().newConnection(); var channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE, "direct", true);
            channel.queueDeclare(QUEUE, true, false, false,
                    Map.of("x-queue-type", "quorum", "x-delivery-limit", -1));
            channel.queueBind(QUEUE, EXCHANGE, ROUTING_KEY);
            DurableMessage event = DurableMessage.create("workflow", "upms", EVENT_ID,
                    "StartRequested", 1, "{\"value\":1}");
            AMQP.BasicProperties messageProperties = new AMQP.BasicProperties.Builder()
                    .messageId(EVENT_ID).contentType("application/json").deliveryMode(2).build();
            channel.basicPublish(EXCHANGE, ROUTING_KEY, true, messageProperties,
                    new DurableMessageWireCodec().encode(event));
        }
    }

    @Test
    void processWaitsAfterCommitBeforeManualAck() throws Exception {
        assumeTrue("hold".equals(phase()));
        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, manager, properties);
        InboxExecutor executor = new InboxExecutor(inbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> {
                    jdbc.update("INSERT INTO ack_crash_business(event_id, value) VALUES (?, 1)", EVENT_ID);
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        CachingConnectionFactory connection = springRabbitFactory();
        SimpleMessageListenerContainer container = container(connection,
                new RabbitInboxListener(executor, inbox, new JdbcQuarantineStore(dataSource, manager),
                        inboundRoute(), this::holdBeforeAck));
        try {
            container.start();
            new CountDownLatch(1).await();
        }
        finally {
            container.stop();
            connection.destroy();
        }
    }

    @Test
    void freshConsumerAcknowledgesRedeliveryWithoutRepeatingBusinessEffect() throws Exception {
        assumeTrue("resume".equals(phase()));
        assertThat(jdbc.queryForObject("SELECT stage FROM ack_crash_marker WHERE event_id=?",
                String.class, EVENT_ID)).isEqualTo("COMMITTED_BEFORE_ACK");
        assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner='upms' AND event_id=?",
                String.class, EVENT_ID)).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT value FROM ack_crash_business WHERE event_id=?",
                Integer.class, EVENT_ID)).isOne();

        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, manager, properties);
        InboxExecutor executor = new InboxExecutor(inbox, "upms", Map.of(
                new InboxExecutor.Route("workflow", "StartRequested", 1), message -> {
                    jdbc.update("UPDATE ack_crash_business SET value=value+1 WHERE event_id=?", EVENT_ID);
                    return DurableMessageHandler.Result.PROCESSED;
                }));
        CachingConnectionFactory connection = springRabbitFactory();
        SimpleMessageListenerContainer container = container(connection,
                new RabbitInboxListener(executor, inbox, new JdbcQuarantineStore(dataSource, manager),
                        inboundRoute()));
        boolean stopped = false;
        try {
            container.start();
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (queueDepth() != 0 && System.nanoTime() < deadline) Thread.sleep(50);
            Thread.sleep(250);
            container.stop();
            connection.destroy();
            stopped = true;
            assertThat(queueDepth()).isZero();
            assertThat(jdbc.queryForObject("SELECT value FROM ack_crash_business WHERE event_id=?",
                    Integer.class, EVENT_ID)).isOne();
            assertThat(jdbc.queryForObject("SELECT status FROM reliable_inbox "
                            + "WHERE target_owner='upms' AND event_id=?", String.class, EVENT_ID))
                    .isEqualTo("PROCESSED");
        }
        finally {
            if (!stopped) {
                container.stop();
                connection.destroy();
            }
        }
    }

    private void holdBeforeAck() {
        markerJdbc().update("INSERT INTO ack_crash_marker(event_id, stage) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE stage=VALUES(stage)", EVENT_ID, "COMMITTED_BEFORE_ACK");
        for (;;) {
            try {
                Thread.sleep(1_000);
            }
            catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private SimpleMessageListenerContainer container(CachingConnectionFactory connection,
            RabbitInboxListener listener) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(connection);
        container.setQueueNames(QUEUE);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setConcurrentConsumers(1);
        container.setMaxConcurrentConsumers(1);
        container.setMessageListener(listener);
        container.afterPropertiesSet();
        return container;
    }

    private int queueDepth() throws Exception {
        try (var connection = rabbitFactory().newConnection(); var channel = connection.createChannel()) {
            return channel.queueDeclarePassive(QUEUE).getMessageCount();
        }
    }

    private RabbitDurableTransport.Route inboundRoute() {
        return new RabbitDurableTransport.Route("workflow", "upms", EXCHANGE, ROUTING_KEY);
    }

    private CachingConnectionFactory springRabbitFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(required("RELIABLE_RABBIT_TEST_HOST"));
        factory.setPort(Integer.parseInt(required("RELIABLE_RABBIT_TEST_PORT")));
        factory.setUsername(env("RELIABLE_RABBIT_TEST_USERNAME", "test"));
        factory.setPassword(env("RELIABLE_RABBIT_TEST_PASSWORD", "test"));
        factory.setVirtualHost("/");
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.afterPropertiesSet();
        return factory;
    }

    private ConnectionFactory rabbitFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(required("RELIABLE_RABBIT_TEST_HOST"));
        factory.setPort(Integer.parseInt(required("RELIABLE_RABBIT_TEST_PORT")));
        factory.setUsername(env("RELIABLE_RABBIT_TEST_USERNAME", "test"));
        factory.setPassword(env("RELIABLE_RABBIT_TEST_PASSWORD", "test"));
        return factory;
    }

    private JdbcTemplate markerJdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root",
                System.getenv("MYSQL_ROOT_PASSWORD")));
    }

    private static Path schemaPath(String fileName) {
        Path current = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 5; depth++) {
            Path candidate = current.resolve("bixi-project-documents/sql/migrations").resolve(fileName);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
            if (current == null) break;
        }
        throw new IllegalStateException("Cannot locate migration " + fileName);
    }

    private static String phase() {
        return System.getenv().getOrDefault("RELIABLE_RABBIT_ACK_PHASE", "");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assumeTrue(value != null && !value.isBlank(), name + " is required");
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
