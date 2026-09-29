package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobPersistenceException;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskUtilSchedulingSemanticsTest {

	@Test
	void newJobsRequestRecoveryAfterSchedulerNodeFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getTrigger(any(TriggerKey.class))).thenReturn(null);
		ArgumentCaptor<JobDetail> jobDetail = ArgumentCaptor.forClass(JobDetail.class);

		new TaskUtil().addOrUpateJob(job(), scheduler);

		verify(scheduler).scheduleJob(jobDetail.capture(), any(Trigger.class));
		assertThat(jobDetail.getValue().requestsRecovery()).isTrue();
	}

	@Test
	void concurrentScheduleRaceUsesTheQuartzJobCreatedByTheOtherCaller() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		CronTrigger existingTrigger = mock(CronTrigger.class);
		when(scheduler.getTrigger(TaskUtil.getTriggerKey(job()))).thenReturn(null, existingTrigger);
		doThrow(new ObjectAlreadyExistsException("created concurrently"))
			.when(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));

		new TaskUtil().addOrUpateJob(job(), scheduler);

		verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
		verify(scheduler, times(2)).getTrigger(TaskUtil.getTriggerKey(job()));
	}

	@Test
	void concurrentJdbcDuplicateRaceUsesTheQuartzJobCreatedByTheOtherCaller() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		CronTrigger existingTrigger = mock(CronTrigger.class);
		when(scheduler.getTrigger(TaskUtil.getTriggerKey(job()))).thenReturn(null, existingTrigger);
		JobPersistenceException duplicate = new JobPersistenceException("Couldn't store job",
			new SQLIntegrityConstraintViolationException("Duplicate entry for QRTZ_JOB_DETAILS.PRIMARY"));
		doThrow(duplicate).when(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));

		new TaskUtil().addOrUpateJob(job(), scheduler);

		verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
		verify(scheduler, times(2)).getTrigger(TaskUtil.getTriggerKey(job()));
	}

	@Test
	void concurrentJdbcDuplicateRaceWaitsForTheOtherTransactionToCommit() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		CronTrigger existingTrigger = mock(CronTrigger.class);
		when(scheduler.getTrigger(TaskUtil.getTriggerKey(job()))).thenReturn(null, null, existingTrigger);
		JobPersistenceException duplicate = new JobPersistenceException("Couldn't store job",
			new SQLIntegrityConstraintViolationException("Duplicate entry for QRTZ_JOB_DETAILS.PRIMARY"));
		doThrow(duplicate).when(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));

		new TaskUtil().addOrUpateJob(job(), scheduler);

		verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
		verify(scheduler, times(3)).getTrigger(TaskUtil.getTriggerKey(job()));
	}

	@Test
	void schedulerKeysAreScopedByThePersistedTenant() {
		SysJob firstTenant = job();
		SysJob secondTenant = job();
		secondTenant.setTenantId(84L);

		assertThat(TaskUtil.getKey(firstTenant)).isNotEqualTo(TaskUtil.getKey(secondTenant));
		assertThat(TaskUtil.getTriggerKey(firstTenant)).isNotEqualTo(TaskUtil.getTriggerKey(secondTenant));
	}

	@Test
	void schedulingMigratesTheLegacyUnscopedQuartzIdentity() throws Exception {
		SysJob job = job();
		Scheduler scheduler = mock(Scheduler.class);
		TriggerKey legacyTrigger = TriggerKey.triggerKey(job.getName(), job.getGroup());
		JobKey legacyJob = JobKey.jobKey(job.getName(), job.getGroup());
		when(scheduler.getTrigger(legacyTrigger)).thenReturn(mock(CronTrigger.class));
		when(scheduler.getTrigger(TaskUtil.getTriggerKey(job))).thenReturn(null);

		new TaskUtil().addOrUpateJob(job, scheduler);

		verify(scheduler).pauseTrigger(legacyTrigger);
		verify(scheduler).unscheduleJob(legacyTrigger);
		verify(scheduler).deleteJob(legacyJob);
	}

	@Test
	void removingAnAlreadyMissingJobIsIdempotent() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getTrigger(TaskUtil.getTriggerKey(job()))).thenReturn(null);
		when(scheduler.checkExists(TaskUtil.getKey(job()))).thenReturn(false);

		new TaskUtil().removeJob(job(), scheduler);

		verify(scheduler, never()).pauseTrigger(TaskUtil.getTriggerKey(job()));
		verify(scheduler, never()).unscheduleJob(TaskUtil.getTriggerKey(job()));
		verify(scheduler, never()).deleteJob(TaskUtil.getKey(job()));
	}

	@Test
	void mapsEverySupportedMisfirePolicyToQuartz() throws Exception {
		assertMisfireInstruction(BixiQuartzEnum.MISFIRE_DEFAULT, Trigger.MISFIRE_INSTRUCTION_SMART_POLICY);
		assertMisfireInstruction(BixiQuartzEnum.MISFIRE_IGNORE_MISFIRES,
			CronTrigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY);
		assertMisfireInstruction(BixiQuartzEnum.MISFIRE_FIRE_AND_PROCEED,
			CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
		assertMisfireInstruction(BixiQuartzEnum.MISFIRE_DO_NOTHING,
			CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
	}

	private void assertMisfireInstruction(BixiQuartzEnum policy, int expected) throws Exception {
		SysJob job = job();
		job.setMisfirePolicy(policy.getType());
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getTrigger(any(TriggerKey.class))).thenReturn(null);
		ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);

		new TaskUtil().addOrUpateJob(job, scheduler);

		verify(scheduler).scheduleJob(any(JobDetail.class), trigger.capture());
		assertThat(trigger.getValue()).isInstanceOf(CronTrigger.class);
		assertThat(trigger.getValue().getMisfireInstruction()).isEqualTo(expected);
	}

	private SysJob job() {
		SysJob job = SysJob.builder()
			.name("billing")
			.group("finance")
			.cronExpression("0 0/5 * * * ?")
			.misfirePolicy(BixiQuartzEnum.MISFIRE_DO_NOTHING.getType())
			.status(BixiQuartzEnum.JOB_STATUS_RUNNING.getType())
			.build();
		job.setTenantId(42L);
		return job;
	}

}
