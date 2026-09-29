package com.lotus.bixi.upms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.service.impl.NoticeSseEmitterRegistry;
import com.lotus.bixi.upms.service.impl.NoticeSseRedisBroadcaster;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;

/** Optional Redis fan-out for SSE; without Redis the local emitter registry remains active. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({RedisConnectionFactory.class, StringRedisTemplate.class})
public class NoticeSseRedisConfiguration {

    @Bean
    NoticeSseRedisBroadcaster noticeSseRedisBroadcaster(StringRedisTemplate redisTemplate,
                                                        NoticeSseEmitterRegistry registry,
                                                        ObjectMapper objectMapper) {
        return new NoticeSseRedisBroadcaster(redisTemplate, registry, objectMapper,
                UUID.randomUUID().toString());
    }

    @Bean(destroyMethod = "stop")
    RedisMessageListenerContainer noticeSseRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            NoticeSseRedisBroadcaster broadcaster) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(broadcaster, new ChannelTopic(NoticeSseRedisBroadcaster.TOPIC));
        return container;
    }
}
