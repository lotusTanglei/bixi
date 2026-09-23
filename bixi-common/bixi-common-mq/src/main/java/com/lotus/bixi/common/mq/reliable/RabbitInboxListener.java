package com.lotus.bixi.common.mq.reliable;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

import java.io.IOException;
import java.util.Objects;

/**
 * Manual-ack Rabbit listener for one owner's inbox queue. Decodes the wire bytes,
 * validates the routing against the fixed ingress route, and delegates to the inbox
 * executor. The executor persists the message before processing, so the listener
 * acknowledges once the inbox has recorded a terminal outcome (PROCESSED, IGNORED,
 * or FAILED). Retryable failures leave the message in the inbox for scheduled
 * recovery; the listener does not requeue to Rabbit.
 *
 * <p>Messages that cannot be decoded or do not match the ingress route are quarantined
 * and acknowledged, since redelivery would repeat the same rejection.
 */
public final class RabbitInboxListener implements ChannelAwareMessageListener {
    private static final Logger LOG = LoggerFactory.getLogger(RabbitInboxListener.class);

    private final InboxExecutor executor;
    private final JdbcInboxStore inbox;
    private final JdbcQuarantineStore quarantine;
    private final RabbitDurableTransport.Route inboundRoute;
    private final DurableMessageWireCodec codec;
    private final Runnable beforeAck;

    public RabbitInboxListener(InboxExecutor executor, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, RabbitDurableTransport.Route inboundRoute) {
        this(executor, inbox, quarantine, inboundRoute, () -> { });
    }

    RabbitInboxListener(InboxExecutor executor, JdbcInboxStore inbox,
            JdbcQuarantineStore quarantine, RabbitDurableTransport.Route inboundRoute,
            Runnable beforeAck) {
        this.executor = Objects.requireNonNull(executor, "Inbox executor is required");
        this.inbox = Objects.requireNonNull(inbox, "Inbox store is required");
        this.quarantine = Objects.requireNonNull(quarantine, "Quarantine store is required");
        this.inboundRoute = Objects.requireNonNull(inboundRoute, "Inbound route is required");
        this.codec = new DurableMessageWireCodec();
        this.beforeAck = Objects.requireNonNull(beforeAck, "Before-ack hook is required");
    }

    @Override
    public void onMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        String evidenceId = messageId != null ? messageId : "unknown-" + deliveryTag;

        try {
            DurableMessage durable = codec.decode(message.getBody());
            validateRouting(durable);
            executor.receive(durable);
            beforeAck.run();
            channel.basicAck(deliveryTag, false);
        }
        catch (IOException ioFailure) {
            LOG.error("Rabbit channel I/O failure for {}: {}", evidenceId, ioFailure.getMessage(), ioFailure);
        }
        catch (IllegalArgumentException decodeFailure) {
            LOG.warn("Quarantining undecodable inbox message {}: {}", evidenceId, decodeFailure.getMessage());
            quarantine(evidenceId, safeBody(message), "WIRE_FORMAT_INVALID");
            ackSilently(channel, deliveryTag);
        }
        catch (InboxDeliveryException deliveryFailure) {
            handleInboxFailure(deliveryFailure, evidenceId, message, channel, deliveryTag);
        }
        catch (RuntimeException unexpectedFailure) {
            LOG.error("Unexpected inbox delivery failure for {}: {}",
                    evidenceId, unexpectedFailure.getMessage(), unexpectedFailure);
            quarantine(evidenceId, safeBody(message), "UNEXPECTED_FAILURE");
            ackSilently(channel, deliveryTag);
        }
    }

    private void validateRouting(DurableMessage message) {
        if (!inboundRoute.targetOwner().equals(message.targetOwner())) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Message target does not match the ingress route");
        }
        if (!inboundRoute.sourceOwner().equals(message.sourceOwner())) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Message source does not match the ingress route");
        }
    }

    private void handleInboxFailure(InboxDeliveryException failure, String evidenceId,
            Message message, Channel channel, long deliveryTag) {
        switch (failure.kind()) {
            case PERMANENT, CONFLICT -> {
                LOG.warn("Quarantining permanently rejected inbox message {}: {}",
                        evidenceId, failure.getMessage());
                quarantine(evidenceId, safeBody(message), failure.kind().name());
                ackSilently(channel, deliveryTag);
            }
            case RETRYABLE -> {
                LOG.info("Inbox message {} is retryable; recovery will redeliver: {}",
                        evidenceId, failure.getMessage());
                ackSilently(channel, deliveryTag);
            }
        }
    }

    private void quarantine(String evidenceId, String body, String reason) {
        // A missing failure record is not a terminal outcome. Propagate the store
        // failure so manual ack remains outstanding and the broker can redeliver.
        quarantine.quarantine(evidenceId, body, reason);
    }

    private void ackSilently(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        }
        catch (Exception ackFailure) {
            LOG.warn("Inbox ack failed for delivery tag {}: {}", deliveryTag, ackFailure.getMessage());
        }
    }

    private static String safeBody(Message message) {
        try {
            // Replay needs the exact wire envelope; the quarantine table is LONGTEXT and
            // DurableMessageWireCodec performs the actual envelope/payload validation.
            return new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (RuntimeException bodyFailure) {
            return "BODY_UNREADABLE";
        }
    }
}
