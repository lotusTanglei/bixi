package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.entity.SysJob;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskUtilSchedulerFailureTest {

	private final TaskUtil taskUtil = new TaskUtil();

	@Test
	void addOrUpdatePropagatesSchedulerFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getTrigger(any(TriggerKey.class))).thenThrow(new SchedulerException("offline"));

		assertSchedulerFailure(() -> taskUtil.addOrUpateJob(job(), scheduler), "添加或更新");
	}

	@Test
	void pausePropagatesSchedulerFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		doThrow(new SchedulerException("offline")).when(scheduler).pauseJob(any(JobKey.class));

		assertSchedulerFailure(() -> taskUtil.pauseJob(job(), scheduler), "暂停任务");
	}

	@Test
	void resumePropagatesSchedulerFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		doThrow(new SchedulerException("offline")).when(scheduler).resumeJob(any(JobKey.class));

		assertSchedulerFailure(() -> taskUtil.resumeJob(job(), scheduler), "恢复任务");
	}

	@Test
	void removePropagatesSchedulerFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		when(scheduler.getTrigger(TaskUtil.getTriggerKey(job()))).thenReturn(mock(Trigger.class));
		doThrow(new SchedulerException("offline")).when(scheduler).pauseTrigger(any(TriggerKey.class));

		assertSchedulerFailure(() -> taskUtil.removeJob(job(), scheduler), "删除任务");
	}

	@Test
	void resumeAllPropagatesSchedulerFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		doThrow(new SchedulerException("offline")).when(scheduler).resumeAll();

		assertSchedulerFailure(() -> taskUtil.startJobs(scheduler), "启动全部任务");
	}

	@Test
	void pauseAllPropagatesSchedulerFailure() throws Exception {
		Scheduler scheduler = mock(Scheduler.class);
		doThrow(new SchedulerException("offline")).when(scheduler).pauseAll();

		assertSchedulerFailure(() -> taskUtil.pauseJobs(scheduler), "暂停全部任务");
	}

	private void assertSchedulerFailure(Runnable operation, String action) {
		assertThatThrownBy(operation::run)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(action)
			.hasRootCauseInstanceOf(SchedulerException.class);
	}

	private SysJob job() {
		SysJob job = SysJob.builder()
			.name("billing")
			.group("finance")
			.cronExpression("0 0/5 * * * ?")
			.misfirePolicy("3")
			.status("3")
			.build();
		job.setTenantId(42L);
		return job;
	}

}
