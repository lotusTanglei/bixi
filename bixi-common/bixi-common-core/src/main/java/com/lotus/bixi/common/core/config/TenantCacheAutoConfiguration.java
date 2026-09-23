package com.lotus.bixi.common.core.config;

import com.lotus.bixi.common.core.cache.TenantCacheInvalidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/** Exposes tenant cache invalidation to deployments with narrow component scans. */
@AutoConfiguration
@ConditionalOnBean(RedisTemplate.class)
public class TenantCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TenantCacheInvalidator tenantCacheInvalidator(RedisTemplate<String, Object> redisTemplate) {
        return new TenantCacheInvalidator(redisTemplate);
    }
}
