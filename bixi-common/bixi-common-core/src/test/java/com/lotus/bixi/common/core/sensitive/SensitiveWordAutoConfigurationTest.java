package com.lotus.bixi.common.core.sensitive;

import com.lotus.bixi.common.core.config.SensitiveWordAutoConfiguration;
import com.lotus.bixi.common.core.cache.TenantCacheInvalidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveWordAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(SensitiveWordAutoConfiguration.class,
					com.lotus.bixi.common.core.config.TenantCacheAutoConfiguration.class))
			.withBean(RedisTemplate.class, TestRedisTemplate::new);

	@Test
	void exposesSensitiveWordEngineToCloudModuleContexts() {
		contextRunner.run(context -> assertThat(context)
				.hasSingleBean(SensitiveWordEngine.class)
				.hasSingleBean(TenantCacheInvalidator.class));
	}

	static class TestRedisTemplate extends RedisTemplate<String, Object> {
		@Override
		public void afterPropertiesSet() {
			// No connection factory is needed to verify auto-configuration wiring.
		}
	}
}
