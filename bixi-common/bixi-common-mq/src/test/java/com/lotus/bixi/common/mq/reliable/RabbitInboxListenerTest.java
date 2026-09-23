package com.lotus.bixi.common.mq.reliable;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RabbitInboxListenerTest {
    @Test
    void quarantinePersistenceFailureLeavesDeliveryUnacknowledged() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:1/nope", "test", "test");
        var manager = new DataSourceTransactionManager(dataSource);
        var inbox = new JdbcInboxStore(dataSource, manager, ReliableDeliveryProperties.defaults());
        var executor = new InboxExecutor(inbox, "workflow", java.util.Map.of());
        var quarantine = new JdbcQuarantineStore(dataSource, manager);
        var route = new RabbitDurableTransport.Route("upms", "workflow", "bixi.workflow", "workflow.upms");
        var listener = new RabbitInboxListener(executor, inbox, quarantine, route);
        AtomicInteger acknowledgements = new AtomicInteger();
        Channel channel = (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(),
                new Class<?>[]{Channel.class}, (proxy, method, args) -> {
                    if ("basicAck".equals(method.getName())) {
                        acknowledgements.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
        var properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        properties.setMessageId("bad-message");

        assertThatThrownBy(() -> listener.onMessage(
                new Message("not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties), channel))
                .isInstanceOf(RuntimeException.class);

        assertThat(acknowledgements).hasValue(0);
    }

    @Test
    void quarantineBodyKeepsTheCompleteWireForLaterReplay() throws Exception {
        String body = "{\"payload\":\"" + "x".repeat(5000) + "\"}";
        var properties = new MessageProperties();
        Message message = new Message(body.getBytes(StandardCharsets.UTF_8), properties);
        Method safeBody = RabbitInboxListener.class.getDeclaredMethod("safeBody", Message.class);
        safeBody.setAccessible(true);

        assertThat(safeBody.invoke(null, message)).isEqualTo(body);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
