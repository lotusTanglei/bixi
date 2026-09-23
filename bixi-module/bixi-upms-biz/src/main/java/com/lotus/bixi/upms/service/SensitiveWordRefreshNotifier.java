package com.lotus.bixi.upms.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Broadcasts tenant-specific sensitive-word reloads to every UPMS instance. */
@Component
@RequiredArgsConstructor
public class SensitiveWordRefreshNotifier {

    public static final String TOPIC = "bixi:sensitive-word:refresh";

    private final StringRedisTemplate redisTemplate;

    public void publish(Long tenantId) {
        if (tenantId != null) {
            redisTemplate.convertAndSend(TOPIC, tenantId.toString());
        }
    }
}
