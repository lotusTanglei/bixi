package com.lotus.bixi.quartz;

import com.lotus.bixi.quartz.config.BixiQuartzConfig;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QuartzDualModeConfigurationTest {

	@Test
	void singleSkipsCloudEntryPointButKeepsSharedQuartzConfiguration() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("test", Map.of("bixi.deployment.mode", "single")));
			Scheduler scheduler = mock(Scheduler.class);
			SchedulerFactoryBean factory = new TestSchedulerFactoryBean(scheduler);
			context.registerBean("quartzScheduler", SchedulerFactoryBean.class, () -> factory);
			context.register(BixiQuartzApplication.class, BixiQuartzConfig.class);

			context.refresh();

			assertThat(context.getBeansOfType(SchedulerFactoryBean.class)).hasSize(1);
			assertThat(context.getBeansOfType(Scheduler.class)).hasSize(1);
			assertThat(context.containsBean("bixiQuartzConfig")).isTrue();
			assertThat(context.containsBean("bixiQuartzApplication")).isFalse();
		}
	}

	@Test
	void cloudEntryPointOwnsOnlyTheCloudConditional() {
		ConditionalOnProperty condition = BixiQuartzApplication.class.getAnnotation(ConditionalOnProperty.class);

		assertThat(condition).isNotNull();
		assertThat(condition.name()).containsExactly("bixi.deployment.mode");
		assertThat(condition.havingValue()).isEqualTo("cloud");
		assertThat(condition.matchIfMissing()).isTrue();
		assertThat(BixiQuartzConfig.class.getAnnotation(ConditionalOnProperty.class)).isNull();
	}

	private static final class TestSchedulerFactoryBean extends SchedulerFactoryBean {

		private final Scheduler scheduler;

		private TestSchedulerFactoryBean(Scheduler scheduler) {
			this.scheduler = scheduler;
		}

		@Override
		public Scheduler getScheduler() {
			return this.scheduler;
		}

		@Override
		public void afterPropertiesSet() {
			// Keep the context test independent of a JDBC or RAM Quartz store.
		}

		@Override
		public void start() {
			// No external scheduler should be started by this wiring test.
		}

		@Override
		public void stop() {
			// No external scheduler should be stopped by this wiring test.
		}

		@Override
		public void destroy() {
			// No external scheduler should be destroyed by this wiring test.
		}

	}

}
