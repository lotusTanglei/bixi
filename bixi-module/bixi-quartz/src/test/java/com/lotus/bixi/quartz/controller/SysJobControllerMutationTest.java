package com.lotus.bixi.quartz.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.quartz.dto.SysJobMutationDTO;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.service.SysJobRecordService;
import com.lotus.bixi.quartz.service.SysJobService;
import com.lotus.bixi.quartz.util.TaskUtil;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysJobControllerMutationTest {

	@Test
	void startAllDoesNotPersistRunningStateWhenSchedulerFails() throws Exception {
		SysJobService service = mock(SysJobService.class);
		Scheduler scheduler = mock(Scheduler.class);
		doThrow(new SchedulerException("offline")).when(scheduler).resumeAll();
		SysJobController controller = controller(service, scheduler);

		assertThatThrownBy(controller::startJobs)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("启动全部任务");

		verify(service, never()).update(any(SysJob.class), any(Wrapper.class));
	}

	@Test
	void shutdownDoesNotPersistPausedStateWhenSchedulerFails() throws Exception {
		SysJobService service = mock(SysJobService.class);
		SysJob running = job(BixiQuartzEnum.JOB_STATUS_RUNNING.getType());
		when(service.getById(7L)).thenReturn(running);
		Scheduler scheduler = mock(Scheduler.class);
		doThrow(new SchedulerException("offline")).when(scheduler).pauseJob(any(JobKey.class));
		SysJobController controller = controller(service, scheduler);

		assertThatThrownBy(() -> controller.shutdownJob(7L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("暂停任务");

		verify(service, never()).updateById(any(SysJob.class));
	}

	@Test
	void runningJobCannotBeEditedBehindTheUiGuard() {
		SysJobService service = mock(SysJobService.class);
		when(service.getById(7L)).thenReturn(job(BixiQuartzEnum.JOB_STATUS_RUNNING.getType()));
		SysJobController controller = controller(service, mock(Scheduler.class));
		SysJobMutationDTO update = request(7L);

		assertThatThrownBy(() -> controller.updateById(update))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("运行中");

		verify(service, never()).updateById(any(SysJob.class));
	}

	@Test
	void missingJobsReturnFailuresWithoutCallingQuartz() throws Exception {
		SysJobService service = mock(SysJobService.class);
		Scheduler scheduler = mock(Scheduler.class);
		SysJobController controller = controller(service, scheduler);

		R<?> detail = controller.getById(404L);
		R<?> run = controller.runJob(404L);
		R<?> delete = controller.removeById(404L);

		assertThat(detail.getCode()).isNotZero();
		assertThat(run.getCode()).isNotZero();
		assertThat(delete.getCode()).isNotZero();
		verify(scheduler, never()).checkExists(any(JobKey.class));
	}

	@Test
	void runningJobDeleteIsRejectedInsteadOfReportingSuccess() {
		SysJobService service = mock(SysJobService.class);
		when(service.getById(7L)).thenReturn(job(BixiQuartzEnum.JOB_STATUS_RUNNING.getType()));
		SysJobController controller = controller(service, mock(Scheduler.class));

		R<?> result = controller.removeById(7L);

		assertThat(result.getCode()).isNotZero();
		verify(service, never()).removeById(7L);
	}

	private SysJobController controller(SysJobService service, Scheduler scheduler) {
		return new SysJobController(service, mock(SysJobRecordService.class), new TaskUtil(), scheduler);
	}

	private SysJob job(String status) {
		SysJob job = SysJob.builder()
			.name("billing")
			.group("finance")
			.cronExpression("0 0/5 * * * ?")
			.misfirePolicy("3")
			.status(status)
			.build();
		job.setId(7L);
		job.setTenantId(42L);
		return job;
	}

	private SysJobMutationDTO request(Long id) {
		return SysJobMutationDTO.builder()
			.id(id)
			.name("billing")
			.group("finance")
			.type("2")
			.className("billingTask")
			.methodName("run")
			.cronExpression("0 0/10 * * * ?")
			.misfirePolicy("3")
			.retryCount(0)
			.retryIntervalSeconds(5)
			.build();
	}

}
