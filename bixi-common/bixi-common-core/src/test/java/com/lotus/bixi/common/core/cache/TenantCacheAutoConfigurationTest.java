package com.lotus.bixi.common.core.cache;

import com.lotus.bixi.common.core.config.TenantCacheAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCacheAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantCacheAutoConfiguration.class))
            .withBean(RedisTemplate.class, TestRedisTemplate::new);

    @Test
    void exposesTenantCacheInvalidatorToCloudModuleContexts() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(TenantCacheInvalidator.class));
    }

    public static class TestRedisTemplate extends RedisTemplate<String, Object> {
        @Override
        public void afterPropertiesSet() {
            // No connection factory is needed to verify auto-configuration wiring.
        }
    }
}
