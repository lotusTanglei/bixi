package com.lotus.bixi.common.mq.reliable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.SmartLifecycle;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Combined Rabbit sender and inbox receiver for one owner's cloud-mode endpoint.
 * The private connection factory, template and listener container are created on start
 * and released on stop/close. Outbound delivery selects the route by target owner;
 * inbound validation uses the fixed ingress route that matches the receive queue.
 *
 * <p>Phase 100 starts after the owner's recovery worker (phase 0) and stops before it.
 * Queue declaration with quorum parameters runs at start; a parameter mismatch against
 * an existing queue surfaces as a startup failure rather than a silent consumer error.
 */
public final class RabbitOwnerEndpoint implements DurableTransport, SmartLifecycle, AutoCloseable {
    public static final int PHASE = 100;
    private static final Logger LOG = LoggerFactory.getLogger(RabbitOwnerEndpoint.class);

    private final ReliableRabbitProperties.Settings settings;
    private final Map<String, RabbitDurableTransport.Route> routesByTarget;
    private final RabbitDurableTransport.Route inboundRoute;
    private final String inboxQueue;
    private final InboxExecutor executor;
    private final JdbcInboxStore inbox;
    private final JdbcQuarantineStore quarantine;
    private final ReliableDeliveryProperties properties;
    private final BooleanSupplier recoveryRunning;

    private final Object lifecycle = new Object();
    private final AtomicBoolean stopped = new AtomicBoolean(true);
    private volatile boolean running;
    private volatile boolean closed;

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate template;
    private RabbitAdmin admin;
    private SimpleMessageListenerContainer listenerContainer;

    public RabbitOwnerEndpoint(ReliableRabbitProperties.Settings settings,
            RabbitDurableTransport.Route outbound, RabbitDurableTransport.Route inbound,
            String inboxQueue, InboxExecutor executor, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, ReliableDeliveryProperties properties,
            BooleanSupplier recoveryRunning) {
        this.settings = Objects.requireNonNull(settings, "Rabbit settings are required");
        Objects.requireNonNull(outbound, "Outbound route is required");
        Objects.requireNonNull(inbound, "Inbound route is required");
        this.inboxQueue = requireInbox(inboxQueue);
        this.executor = Objects.requireNonNull(executor, "Inbox executor is required");
        this.inbox = Objects.requireNonNull(inbox, "Inbox store is required");
        this.quarantine = Objects.requireNonNull(quarantine, "Quarantine store is required");
        this.properties = Objects.requireNonNull(properties, "Delivery properties are required");
        this.recoveryRunning = Objects.requireNonNull(recoveryRunning, "Recovery status is required");

        Map<String, RabbitDurableTransport.Route> routes = new HashMap<>();
        if (routes.put(outbound.targetOwner(), outbound) != null)
            throw new IllegalArgumentException("Two routes share the same target owner");
        if (!inbound.targetOwner().equals(executor.targetOwner()))
            throw new IllegalArgumentException("Inbound route target must match the inbox executor owner");
        if (inbound.sourceOwner().equals(inbound.targetOwner()))
            throw new IllegalArgumentException("Inbound source and target must differ");
        routesByTarget = Map.copyOf(routes);
        inboundRoute = inbound;
    }

    @Override
    public void deliver(DurableMessage message) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Rabbit durable delivery must run outside business transactions");
        Objects.requireNonNull(message, "Message is required");
        RabbitDurableTransport.Route route = routesByTarget.get(message.targetOwner());
        if (route == null)
            throw new IllegalArgumentException("No outbound route for target " + message.targetOwner());

        var props = new MessageProperties();
        props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setContentEncoding("UTF-8");
        props.setMessageId(message.eventId());
        var outbound = new org.springframework.amqp.core.Message(
                new DurableMessageWireCodec().encode(message), props);
        var correlation = new CorrelationData(UUID.randomUUID().toString());
        long deadline = System.nanoTime() + properties.sendTimeout().toNanos();
        template.send(route.exchange(), route.routingKey(), outbound, correlation);
        try {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new TimeoutException();
            var confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null)
                throw new org.springframework.amqp.AmqpException(
                        "Rabbit did not confirm a routed durable delivery");
        }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new org.springframework.amqp.AmqpException("Rabbit durable delivery interrupted");
        }
        catch (TimeoutException failure) {
            throw new org.springframework.amqp.AmqpException("Rabbit durable delivery confirmation timed out");
        }
        catch (ExecutionException failure) {
            throw new org.springframework.amqp.AmqpException("Rabbit durable delivery confirmation failed");
        }
    }

    @Override
    public void start() {
        synchronized (lifecycle) {
            if (closed) throw new IllegalStateException("A closed Rabbit endpoint requires a new instance");
            if (running) return;
            connectionFactory = new CachingConnectionFactory();
            connectionFactory.setHost(settings.host());
            connectionFactory.setPort(settings.port());
            connectionFactory.setUsername(settings.username());
            connectionFactory.setPassword(settings.password());
            connectionFactory.setVirtualHost(settings.virtualHost());
            connectionFactory.setConnectionTimeout(settings.connectionTimeout());
            connectionFactory.setCloseTimeout(settings.shutdownTimeout());
            try {
                connectionFactory.getRabbitConnectionFactory().setHandshakeTimeout(settings.handshakeTimeout());
            }
            catch (Exception handshakeFailure) {
                throw new IllegalStateException("Cannot configure Rabbit handshake timeout", handshakeFailure);
            }
            connectionFactory.setRequestedHeartBeat(settings.requestedHeartbeat());
            connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
            connectionFactory.setPublisherReturns(true);
            connectionFactory.setChannelCacheSize(settings.channelCacheSize());
            connectionFactory.afterPropertiesSet();

            template = new RabbitTemplate(connectionFactory);
            template.setMandatory(true);
            template.setChannelTransacted(false);

            admin = new RabbitAdmin(connectionFactory);
            admin.afterPropertiesSet();
            declareInboxQueue();

            listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
            listenerContainer.setQueueNames(inboxQueue);
            listenerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
            listenerContainer.setPrefetchCount(settings.prefetch());
            listenerContainer.setConcurrentConsumers(1);
            listenerContainer.setMaxConcurrentConsumers(1);
            listenerContainer.setMessageListener(
                    new RabbitInboxListener(executor, inbox, quarantine, inboundRoute));
            listenerContainer.afterPropertiesSet();
            listenerContainer.start();

            running = true;
            stopped.set(false);
            LOG.info("Reliable Rabbit endpoint started for inbox queue {}", inboxQueue);
        }
    }

    private void declareInboxQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-queue-type", "quorum");
        arguments.put("x-delivery-limit", -1);
        Queue queue = new Queue(inboxQueue, true, false, false, arguments);
        DirectExchange exchange = new DirectExchange(inboundRoute.exchange(), true, false);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(inboundRoute.routingKey()));
    }

    @Override
    public void stop() {
        synchronized (lifecycle) {
            if (!running && connectionFactory == null && listenerContainer == null) return;
            running = false;
            stopped.set(true);
            shutdownRabbit();
            LOG.info("Reliable Rabbit endpoint stopped for inbox queue {}", inboxQueue);
        }
    }

    private void shutdownRabbit() {
        SimpleMessageListenerContainer container = listenerContainer;
        listenerContainer = null;
        if (container != null) {
            try { container.stop(); }
            catch (RuntimeException shutdownFailure) {
                LOG.warn("Rabbit listener container stop failed: {}", shutdownFailure.getMessage());
            }
        }
        CachingConnectionFactory factory = connectionFactory;
        connectionFactory = null;
        template = null;
        admin = null;
        if (factory != null) {
            try { factory.destroy(); }
            catch (Exception destroyFailure) {
                LOG.warn("Rabbit connection factory close failed: {}", destroyFailure.getMessage());
            }
        }
    }

    @Override public void close() { stop(); }

    @Override public boolean isRunning() { return running; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return PHASE; }

    @Override
    public void stop(Runnable callback) {
        try { stop(); }
        finally { callback.run(); }
    }

    private static String requireInbox(String queue) {
        if (queue == null || queue.isBlank()) throw new IllegalArgumentException("Inbox queue is required");
        return queue;
    }
}
