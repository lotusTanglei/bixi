package com.lotus.bixi.quartz.config;

import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerMetaData;
import org.springframework.boot.actuate.health.Health;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuartzSchedulerHealthIndicatorTest {

	@Test
	void reportsRunningSchedulerAsUpWithOperationalDetails() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getMetaData()).thenReturn(metadata(true, false, false));

		Health health = new QuartzSchedulerHealthIndicator(scheduler).health();

		assertThat(health.getStatus().getCode()).isEqualTo("UP");
		assertThat(health.getDetails())
			.containsEntry("state", "RUNNING")
			.containsEntry("schedulerName", "bixiQuartz")
			.containsEntry("instanceId", "node-a")
			.containsEntry("clustered", true)
			.containsEntry("jobsExecuted", 3)
			.containsEntry("threadPoolSize", 8);
	}

	@Test
	void marksStandbySchedulerOutOfServiceSoSbaDoesNotRouteWorkToIt() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getMetaData()).thenReturn(metadata(true, true, false));

		Health health = new QuartzSchedulerHealthIndicator(scheduler).health();

		assertThat(health.getStatus().getCode()).isEqualTo("OUT_OF_SERVICE");
		assertThat(health.getDetails()).containsEntry("state", "STANDBY");
	}

	@Test
	void marksShutdownSchedulerDown() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getMetaData()).thenReturn(metadata(false, false, true));

		Health health = new QuartzSchedulerHealthIndicator(scheduler).health();

		assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
		assertThat(health.getDetails()).containsEntry("state", "SHUTDOWN");
	}

	@Test
	void marksSchedulerThatHasNotStartedOutOfService() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getMetaData()).thenReturn(metadata(false, false, false));

		Health health = new QuartzSchedulerHealthIndicator(scheduler).health();

		assertThat(health.getStatus().getCode()).isEqualTo("OUT_OF_SERVICE");
		assertThat(health.getDetails()).containsEntry("state", "NOT_STARTED");
	}

	@Test
	void turnsSchedulerMetadataFailureIntoDownHealth() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getMetaData()).thenThrow(new SchedulerException("store unavailable"));

		Health health = new QuartzSchedulerHealthIndicator(scheduler).health();

		assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
		assertThat(health.getDetails())
			.containsEntry("state", "ERROR")
			.containsEntry("error", "SchedulerException");
	}

	private SchedulerMetaData metadata(boolean started, boolean standby, boolean shutdown) {
		return new SchedulerMetaData("bixiQuartz", "node-a", getClass(), false, started, standby, shutdown,
			new Date(), 3, getClass(), true, true, getClass(), 8, "2.3.2");
	}

}
