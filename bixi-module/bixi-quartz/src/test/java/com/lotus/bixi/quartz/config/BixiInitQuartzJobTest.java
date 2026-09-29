package com.lotus.bixi.quartz.config;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.service.SysJobService;
import com.lotus.bixi.quartz.util.TaskUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BixiInitQuartzJobTest {

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void loadsJobsWithDefaultTenantAndClearsContext() throws Exception {
		SysJobService sysJobService = mock(SysJobService.class);
		when(sysJobService.list()).thenAnswer(invocation -> {
			assertThat(TenantContextHolder.get()).isEqualTo(SecurityConstants.DEFAULT_TENANT_ID);
			assertThat(TenantContextHolder.isAllTenantsReadOnly()).isTrue();
			return java.util.List.of();
		});

		BixiInitQuartzJob initializer = new BixiInitQuartzJob(sysJobService, new TaskUtil(), mock(Scheduler.class));
		initializer.afterPropertiesSet();

		assertThat(TenantContextHolder.get()).isNull();
	}

	@Test
	void restoresAnExistingAllTenantReadOnlyContextAfterReconciliation() throws Exception {
		TenantContextHolder.set(88L);
		TenantContextHolder.setReadOnlySwitch(true);
		TenantContextHolder.setAllTenantsReadOnly(true);
		SysJobService sysJobService = mock(SysJobService.class);
		when(sysJobService.list()).thenReturn(java.util.List.of());

		new BixiInitQuartzJob(sysJobService, new TaskUtil(), mock(Scheduler.class)).afterPropertiesSet();

		assertThat(TenantContextHolder.get()).isEqualTo(88L);
		assertThat(TenantContextHolder.isReadOnlySwitch()).isTrue();
		assertThat(TenantContextHolder.isAllTenantsReadOnly()).isTrue();
	}

	@Test
	void reconcilesRunningAndPausedJobsIntoTheScheduler() throws Exception {
		SysJob running = job("running", BixiQuartzEnum.JOB_STATUS_RUNNING.getType());
		SysJob paused = job("paused", BixiQuartzEnum.JOB_STATUS_NOT_RUNNING.getType());
		SysJob released = job("released", BixiQuartzEnum.JOB_STATUS_RELEASE.getType());
		SysJobService sysJobService = mock(SysJobService.class);
		when(sysJobService.list()).thenReturn(java.util.List.of(running, paused, released));
		TaskUtil taskUtil = mock(TaskUtil.class);
		Scheduler scheduler = mock(Scheduler.class);

		new BixiInitQuartzJob(sysJobService, taskUtil, scheduler).afterPropertiesSet();

		verify(taskUtil).addOrUpateJob(running, scheduler);
		verify(taskUtil).addOrUpateJob(paused, scheduler);
		verify(taskUtil).removeJob(released, scheduler);
		verify(taskUtil, never()).resumeJob(any(SysJob.class), any(Scheduler.class));
		verify(taskUtil, never()).pauseJob(any(SysJob.class), any(Scheduler.class));
	}

	private SysJob job(String name, String status) {
		return SysJob.builder()
			.name(name)
			.group("startup")
			.cronExpression("0 0/5 * * * ?")
			.misfirePolicy(BixiQuartzEnum.MISFIRE_DO_NOTHING.getType())
			.status(status)
			.build();
	}

}
