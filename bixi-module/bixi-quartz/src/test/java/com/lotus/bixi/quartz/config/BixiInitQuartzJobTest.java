package com.lotus.bixi.quartz.config;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.quartz.service.SysJobService;
import com.lotus.bixi.quartz.util.TaskUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
			return java.util.List.of();
		});

		BixiInitQuartzJob initializer = new BixiInitQuartzJob(sysJobService, new TaskUtil(), mock(Scheduler.class));
		initializer.afterPropertiesSet();

		assertThat(TenantContextHolder.get()).isNull();
	}

}
