package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.event.SysJobRecordEvent;
import com.lotus.bixi.quartz.exception.TaskException;
import com.lotus.bixi.quartz.service.SysJobService;
import org.junit.jupiter.api.Test;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskInvokUtilRetryTest {

	@Test
	void retriesWithinTheConfiguredBoundAndRecordsEveryAttempt() {
		List<SysJobRecord> records = new ArrayList<>();
		List<Long> sleeps = new ArrayList<>();
		ApplicationEventPublisher publisher = event -> {
			if (event instanceof SysJobRecordEvent recordEvent) records.add(recordEvent.getSysJobRecord());
		};
		SysJobService jobService = mock(SysJobService.class);
		when(jobService.updateById(any(SysJob.class))).thenReturn(true);
		AtomicInteger calls = new AtomicInteger();
		TaskInvok invoker = job -> {
			if (calls.incrementAndGet() == 1) throw new TaskException("temporary");
		};
		TaskInvokUtil utility = new TaskInvokUtil(publisher, jobService, ignored -> invoker, sleeps::add);
		SysJob job = job(1, 7);
		Trigger trigger = TriggerBuilder.newTrigger().startNow().build();

		utility.invokMethod(job, trigger, "fire-123", false);

		assertThat(calls).hasValue(2);
		assertThat(sleeps).containsExactly(7000L);
		assertThat(records).extracting(SysJobRecord::getExecutionId).containsExactly("fire-123", "fire-123");
		assertThat(records).extracting(SysJobRecord::getAttempt).containsExactly(1, 2);
		assertThat(records).extracting(SysJobRecord::getMaxAttempts).containsOnly(2);
		assertThat(records).extracting(SysJobRecord::getStatus).containsExactly(
			BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getType(), BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getType());
		assertThat(records).extracting(SysJobRecord::getTriggerType).containsOnly("MANUAL");
		assertThat(records).extracting(SysJobRecord::getRecovered).containsOnly(false);
		verify(jobService).updateById(any(SysJob.class));
	}

	@Test
	void exhaustedRetriesRemainFailedAndRecoveryIsVisibleInHistory() {
		List<SysJobRecord> records = new ArrayList<>();
		ApplicationEventPublisher publisher = event -> {
			if (event instanceof SysJobRecordEvent recordEvent) records.add(recordEvent.getSysJobRecord());
		};
		SysJobService jobService = mock(SysJobService.class);
		when(jobService.updateById(any(SysJob.class))).thenReturn(true);
		TaskInvok invoker = job -> { throw new TaskException("still broken"); };
		TaskInvokUtil utility = new TaskInvokUtil(publisher, jobService, ignored -> invoker, ignored -> { });

		assertThatThrownBy(() -> utility.invokMethod(job(1, 1), TriggerBuilder.newTrigger().startNow().build(),
			"recovery-456", true))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("定时任务执行失败");

		assertThat(records).hasSize(2);
		assertThat(records).extracting(SysJobRecord::getStatus)
			.containsOnly(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getType());
		assertThat(records).extracting(SysJobRecord::getTriggerType).containsOnly("RECOVERY");
		assertThat(records).extracting(SysJobRecord::getRecovered).containsOnly(true);
		assertThat(records).extracting(SysJobRecord::getAttempt).containsExactly(1, 2);
	}

	private SysJob job(int retryCount, int retryIntervalSeconds) {
		SysJob job = SysJob.builder()
			.name("retry-job")
			.group("tests")
			.type("2")
			.retryCount(retryCount)
			.retryIntervalSeconds(retryIntervalSeconds)
			.build();
		job.setId(9L);
		job.setTenantId(42L);
		return job;
	}

}
