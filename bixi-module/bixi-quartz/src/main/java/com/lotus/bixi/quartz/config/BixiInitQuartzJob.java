package com.lotus.bixi.quartz.config;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.service.SysJobService;
import com.lotus.bixi.quartz.util.TaskUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 唐磊
 * <p>
 * 初始化加载定时任务
 */
@Slf4j
@Configuration
@AllArgsConstructor
public class BixiInitQuartzJob implements InitializingBean {

	private final SysJobService sysJobService;

	private final TaskUtil taskUtil;

	private final Scheduler scheduler;

	@Override
	public void afterPropertiesSet() throws Exception {
		Long previousTenantId = TenantContextHolder.get();
		boolean previousReadOnly = TenantContextHolder.isReadOnlySwitch();
		boolean previousAllTenantsReadOnly = TenantContextHolder.isAllTenantsReadOnly();
		TenantContextHolder.set(SecurityConstants.DEFAULT_TENANT_ID);
		// Startup reconciliation is a read-only platform operation. Keep the
		// default tenant as the SQL fallback while explicitly bypassing the
		// tenant predicate so every tenant's persisted job is restored.
		TenantContextHolder.setAllTenantsReadOnly(true);
		try {
			sysJobService.list().forEach(sysjob -> {
				if (BixiQuartzEnum.JOB_STATUS_RELEASE.getType().equals(sysjob.getStatus())) {
					taskUtil.removeJob(sysjob, scheduler);
				}
					else if (BixiQuartzEnum.JOB_STATUS_RUNNING.getType().equals(sysjob.getStatus())
							|| BixiQuartzEnum.JOB_STATUS_NOT_RUNNING.getType().equals(sysjob.getStatus())) {
						taskUtil.addOrUpateJob(sysjob, scheduler);
				}
				else {
					taskUtil.removeJob(sysjob, scheduler);
				}
			});
		}
		finally {
			TenantContextHolder.clear();
			if (previousTenantId != null) TenantContextHolder.set(previousTenantId);
			if (previousReadOnly) TenantContextHolder.setReadOnlySwitch(true);
			if (previousAllTenantsReadOnly) TenantContextHolder.setAllTenantsReadOnly(true);
		}
	}

}
