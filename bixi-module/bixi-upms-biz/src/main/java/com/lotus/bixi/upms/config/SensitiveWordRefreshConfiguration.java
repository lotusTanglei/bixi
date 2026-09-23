package com.lotus.bixi.upms.config;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.service.SensitiveWordRefreshNotifier;
import com.lotus.bixi.upms.service.SysSensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/** Redis pub/sub listener for tenant-local sensitive-word cache refreshes. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(RedisConnectionFactory.class)
@Slf4j
public class SensitiveWordRefreshConfiguration {

    @Bean(destroyMethod = "stop")
    RedisMessageListenerContainer sensitiveWordRefreshContainer(
            RedisConnectionFactory connectionFactory,
            SysSensitiveWordService service) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            try {
                Long tenantId = Long.valueOf(payload);
                Long previousTenant = TenantContextHolder.get();
                boolean previousReadOnly = TenantContextHolder.isReadOnlySwitch();
                try {
                    service.reloadTenant(tenantId);
                }
                finally {
                    if (previousTenant == null) {
                        TenantContextHolder.clear();
                    }
                    else {
                        TenantContextHolder.set(previousTenant);
                        TenantContextHolder.setReadOnlySwitch(previousReadOnly);
                    }
                }
            }
            catch (NumberFormatException ex) {
                log.warn("Ignoring malformed sensitive-word refresh message");
            }
        }, new ChannelTopic(SensitiveWordRefreshNotifier.TOPIC));
        return container;
    }
}
