package com.lotus.bixi.quartz.config;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes the Quartz lifecycle state through Actuator/Spring Boot Admin.
 *
 * <p>An application can still answer the normal HTTP health probe while its
 * scheduler is in standby or has already been shut down. That state is not
 * eligible to receive scheduled work, so it must be visible to the monitor as
 * an unhealthy instance.</p>
 */
@Slf4j
@Component("quartzSchedulerHealthIndicator")
public class QuartzSchedulerHealthIndicator implements HealthIndicator {

	private final Scheduler scheduler;

	public QuartzSchedulerHealthIndicator(Scheduler scheduler) {
		this.scheduler = scheduler;
	}

	@Override
	public Health health() {
		try {
			SchedulerMetaData metadata = scheduler.getMetaData();
			Health.Builder health = lifecycleHealth(metadata);
			return health
				.withDetail("schedulerName", metadata.getSchedulerName())
				.withDetail("instanceId", metadata.getSchedulerInstanceId())
				.withDetail("clustered", metadata.isJobStoreClustered())
				.withDetail("jobsExecuted", metadata.getNumberOfJobsExecuted())
				.withDetail("threadPoolSize", metadata.getThreadPoolSize())
				.build();
		}
		catch (SchedulerException failure) {
			log.warn("读取 Quartz 调度器健康状态失败", failure);
			return Health.down()
				.withDetail("state", "ERROR")
				.withDetail("error", failure.getClass().getSimpleName())
				.build();
		}
	}

	private Health.Builder lifecycleHealth(SchedulerMetaData metadata) {
		if (metadata.isShutdown()) {
			return Health.down().withDetail("state", "SHUTDOWN");
		}
		if (!metadata.isStarted()) {
			return Health.outOfService().withDetail("state", "NOT_STARTED");
		}
		if (metadata.isInStandbyMode()) {
			return Health.outOfService().withDetail("state", "STANDBY");
		}
		return Health.up().withDetail("state", "RUNNING");
	}

}
