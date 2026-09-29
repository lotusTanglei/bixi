package com.lotus.bixi.common.mq.reliable;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RabbitOwnerEndpointConfigurationTest {

    @Test
    void rejectsMessageWhoseSourceIsNotTheConfiguredOwner() {
        RabbitOwnerEndpoint endpoint = endpoint(
                new RabbitDurableTransport.Route("workflow", "upms", "exchange", "route"),
                new RabbitDurableTransport.Route("upms", "workflow", "inbound", "workflow.upms"));
        DurableMessage message = DurableMessage.create("other", "upms", UUID.randomUUID().toString(),
                "test.event", 1, "{}");

        assertThatThrownBy(() -> endpoint.deliver(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message source owner does not match outbound route");
    }

    @Test
    void rejectsOutboundRouteWhoseSourceIsNotTheInboundOwner() {
        assertThatThrownBy(() -> endpoint(
                new RabbitDurableTransport.Route("upms", "other", "exchange", "route"),
                new RabbitDurableTransport.Route("upms", "workflow", "inbound", "workflow.upms")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Outbound route source must match inbound target");
    }

    private static RabbitOwnerEndpoint endpoint(RabbitDurableTransport.Route outbound,
            RabbitDurableTransport.Route inbound) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rabbit-owner-route;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        ReliableDeliveryProperties delivery = ReliableDeliveryProperties.defaults();
        JdbcInboxStore inbox = new JdbcInboxStore(dataSource, transactionManager, delivery);
        JdbcQuarantineStore quarantine = new JdbcQuarantineStore(dataSource, transactionManager, "workflow");
        InboxExecutor executor = new InboxExecutor(inbox, "workflow", Map.of());

        return new RabbitOwnerEndpoint(settings(), outbound, inbound,
                "workflow.inbox", executor, inbox, quarantine, delivery, () -> true);
    }

    private static ReliableRabbitProperties.Settings settings() {
        return new ReliableRabbitProperties.Settings("localhost", 5672, "user", "password", "/",
                1_000, 1_000, 1_000, 5, 1, 1);
    }
}
