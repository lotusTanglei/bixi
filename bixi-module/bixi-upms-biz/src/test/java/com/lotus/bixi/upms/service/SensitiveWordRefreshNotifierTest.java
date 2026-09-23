package com.lotus.bixi.upms.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SensitiveWordRefreshNotifierTest {

    @Test
    void publishesTenantIdOnTheSharedRefreshTopic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SensitiveWordRefreshNotifier notifier = new SensitiveWordRefreshNotifier(redis);

        notifier.publish(7L);

        verify(redis).convertAndSend(SensitiveWordRefreshNotifier.TOPIC, "7");
    }
}
