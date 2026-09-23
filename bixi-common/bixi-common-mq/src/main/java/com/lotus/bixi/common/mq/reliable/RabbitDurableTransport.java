package com.lotus.bixi.common.mq.reliable;

/**
 * Routing and transport primitives for one reliable Rabbit direction. The {@link Route}
 * record describes a fixed exchange and routing key binding between two owners; the outer
 * class is not instantiated by the endpoint, which builds its own connection and template.
 */
public final class RabbitDurableTransport {
    private RabbitDurableTransport() {
    }

    /**
     * Fixed binding between a source and target owner. The exchange and routing key are
     * the physical Rabbit addresses; the owner pair is the logical producer-consumer
     * identity that the inbox executor validates on reception.
     */
    public record Route(String sourceOwner, String targetOwner, String exchange, String routingKey) {
        public Route {
            DurableMessage.requireOwner(sourceOwner);
            DurableMessage.requireOwner(targetOwner);
            if (sourceOwner.equals(targetOwner)) {
                throw new IllegalArgumentException("Route source and target owners must differ");
            }
            if (exchange == null || exchange.isBlank()) {
                throw new IllegalArgumentException("Rabbit exchange is required");
            }
            if (routingKey == null || routingKey.isBlank()) {
                throw new IllegalArgumentException("Rabbit routing key is required");
            }
        }
    }
}
