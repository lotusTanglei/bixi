package com.lotus.bixi.quartz.util;

import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.event.SysJobRecordEvent;
import com.lotus.bixi.quartz.service.SysJobService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.Trigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

/**
 * 定时任务反射工具类
 *
 * @author 唐磊
 */
@Slf4j
@Component
public class TaskInvokUtil {

	private final ApplicationEventPublisher publisher;

	private final SysJobService sysJobService;

	private final InvokerResolver invokerResolver;

	private final RetrySleeper retrySleeper;

	@Autowired
	public TaskInvokUtil(ApplicationEventPublisher publisher, SysJobService sysJobService) {
		this(publisher, sysJobService, TaskInvokFactory::getInvoker, Thread::sleep);
	}

	TaskInvokUtil(ApplicationEventPublisher publisher, SysJobService sysJobService,
			InvokerResolver invokerResolver, RetrySleeper retrySleeper) {
		this.publisher = publisher;
		this.sysJobService = sysJobService;
		this.invokerResolver = invokerResolver;
		this.retrySleeper = retrySleeper;
	}

	public void invokMethod(SysJob sysJob, Trigger trigger) {
		invokMethod(sysJob, trigger, UUID.randomUUID().toString(), false);
	}

	public void invokMethod(SysJob sysJob, Trigger trigger, String executionId, boolean recovering) {
		Long tenantId = sysJob.getTenantId();
		if (tenantId == null || tenantId <= 0) {
			throw new IllegalArgumentException("定时任务缺少有效租户");
		}
		if (executionId == null || executionId.isBlank() || executionId.length() > 128) {
			throw new IllegalArgumentException("定时任务执行标识无效");
		}
		Long previousTenantId = TenantContextHolder.get();
		boolean previousReadOnly = TenantContextHolder.isReadOnlySwitch();
		boolean previousAllTenantsReadOnly = TenantContextHolder.isAllTenantsReadOnly();
		TenantContextHolder.set(tenantId);
		TenantContextHolder.setReadOnlySwitch(false);
		TenantContextHolder.setAllTenantsReadOnly(false);
		try {
			doInvoke(sysJob, trigger, executionId, recovering);
		}
		finally {
			TenantContextHolder.clear();
			if (previousTenantId != null) {
				TenantContextHolder.set(previousTenantId);
			}
			if (previousReadOnly) {
				TenantContextHolder.setReadOnlySwitch(true);
			}
			if (previousAllTenantsReadOnly) {
				TenantContextHolder.setAllTenantsReadOnly(true);
			}
		}
	}

	private void doInvoke(SysJob sysJob, Trigger trigger, String executionId, boolean recovering) {
		int retryCount = boundedRetryCount(sysJob.getRetryCount());
		int maxAttempts = retryCount + 1;
		long retryIntervalMillis = retryIntervalMillis(sysJob.getRetryIntervalSeconds());
		String triggerType = recovering ? "RECOVERY" : trigger instanceof CronTrigger ? "CRON" : "MANUAL";
		// 更新定时任务表内的状态、执行时间、上次执行时间、下次执行时间等信息
		SysJob updateSysjob = new SysJob();
		updateSysjob.setId(sysJob.getId());
		Throwable finalFailure = null;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			long startTime = System.currentTimeMillis();
			SysJobRecord record = record(sysJob, executionId, attempt, maxAttempts, triggerType, recovering);
			Throwable attemptFailure = null;
			try {
				invokerResolver.resolve(sysJob.getType()).invokMethod(sysJob);
				record.setMessage(BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getDescription());
				record.setStatus(BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getType());
				updateSysjob.setExecuteStatus(BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getType());
			}
			catch (Throwable failure) {
				attemptFailure = failure;
				finalFailure = failure;
				log.error("定时任务执行失败，任务名称：{}；任务组名：{}；尝试：{}/{}；执行时间：{}",
					sysJob.getName(), sysJob.getGroup(), attempt, maxAttempts, new Date(), failure);
				record.setMessage(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getDescription());
				record.setStatus(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getType());
				record.setExceptionInfo(exceptionInfo(failure));
				updateSysjob.setExecuteStatus(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getType());
			}
			finally {
				record.setExecuteTime(String.valueOf(System.currentTimeMillis() - startTime));
				publisher.publishEvent(new SysJobRecordEvent(record));
			}
			if (attemptFailure == null) {
				finalFailure = null;
				break;
			}
			if (attempt < maxAttempts) {
				try {
					retrySleeper.sleep(retryIntervalMillis);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					finalFailure = interrupted;
					break;
				}
			}
		}
		copyTriggerTimes(trigger, updateSysjob);
		sysJobService.updateById(updateSysjob);
		if (finalFailure != null) {
			throw new IllegalStateException("定时任务执行失败: " + sysJob.getName(), finalFailure);
		}
	}

	private SysJobRecord record(SysJob job, String executionId, int attempt, int maxAttempts,
			String triggerType, boolean recovering) {
		SysJobRecord record = new SysJobRecord();
		record.setJobId(job.getId());
		record.setTenantId(job.getTenantId());
		record.setExecutionId(executionId);
		record.setAttempt(attempt);
		record.setMaxAttempts(maxAttempts);
		record.setTriggerType(triggerType);
		record.setRecovered(recovering);
		return record;
	}

	private void copyTriggerTimes(Trigger trigger, SysJob update) {
		if (!(trigger instanceof CronTrigger)) return;
		if (trigger.getStartTime() != null) {
			update.setStartTime(trigger.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
		}
		if (trigger.getPreviousFireTime() != null) {
			update.setPreviousTime(trigger.getPreviousFireTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
		}
		if (trigger.getNextFireTime() != null) {
			update.setNextTime(trigger.getNextFireTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
		}
	}

	private int boundedRetryCount(Integer configured) {
		return configured == null ? 0 : Math.max(0, Math.min(configured, 5));
	}

	private long retryIntervalMillis(Integer configuredSeconds) {
		int seconds = configuredSeconds == null ? 5 : Math.max(1, Math.min(configuredSeconds, 300));
		return seconds * 1000L;
	}

	private String exceptionInfo(Throwable failure) {
		String message = failure.getClass().getName() + ": " + String.valueOf(failure.getMessage());
		return StrUtil.sub(message, 0, 2000);
	}

	@FunctionalInterface
	interface InvokerResolver {
		TaskInvok resolve(String type) throws Exception;
	}

	@FunctionalInterface
	interface RetrySleeper {
		void sleep(long millis) throws InterruptedException;
	}

}
