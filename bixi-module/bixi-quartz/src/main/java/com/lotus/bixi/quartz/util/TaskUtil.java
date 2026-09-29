package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.config.BixiQuartzFactory;
import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

/**
 * 定时任务的工具类
 *
 * @author 唐磊
 */
@Slf4j
@Component
public class TaskUtil {

	/**
	 * 获取定时任务的唯一key
	 * @param sysjob
	 * @return
	 */
	public static JobKey getKey(SysJob sysjob) {
		return JobKey.jobKey(sysjob.getName(), tenantGroup(sysjob));
	}

	/**
	 * 获取定时任务触发器cron的唯一key
	 * @param sysjob
	 * @return
	 */
	public static TriggerKey getTriggerKey(SysJob sysjob) {
		return TriggerKey.triggerKey(sysjob.getName(), tenantGroup(sysjob));
	}

	private static String tenantGroup(SysJob sysjob) {
		Long tenantId = sysjob.getTenantId();
		if (tenantId == null || tenantId <= 0) {
			throw new IllegalArgumentException("定时任务缺少有效租户");
		}
		return "tenant-" + tenantId + ":" + sysjob.getGroup();
	}

	/**
	 * 添加或更新定时任务
	 * @param sysjob
	 * @param scheduler
	 */
	public void addOrUpateJob(SysJob sysjob, Scheduler scheduler) {
		CronTrigger trigger = null;
		try {
			removeLegacyJob(sysjob, scheduler);
			JobKey jobKey = getKey(sysjob);
			// 获得触发器
			TriggerKey triggerKey = getTriggerKey(sysjob);
			trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
			// 判断触发器是否存在（如果存在说明之前运行过但是在当前被禁用了，如果不存在说明一次都没运行过）
			if (trigger == null) {
				// 新建一个工作任务 指定任务类型为串接进行的
					JobDetail jobDetail = JobBuilder.newJob(BixiQuartzFactory.class)
						.withIdentity(jobKey)
						.requestRecovery(true)
						.build();
				// 将任务信息添加到任务信息中
				jobDetail.getJobDataMap().put(BixiQuartzEnum.SCHEDULE_JOB_KEY.getType(), sysjob);
				// 将cron表达式进行转换
				CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(sysjob.getCronExpression());
				cronScheduleBuilder = this.handleCronScheduleMisfirePolicy(sysjob, cronScheduleBuilder);
				// 创建触发器并将cron表达式对象给塞入
				trigger = TriggerBuilder.newTrigger()
					.withIdentity(triggerKey)
					.withSchedule(cronScheduleBuilder)
					.build();
				// 在调度器中将触发器和任务进行组合
				scheduler.scheduleJob(jobDetail, trigger);
			}
			else {
				CronScheduleBuilder cronScheduleBuilder = CronScheduleBuilder.cronSchedule(sysjob.getCronExpression());
				cronScheduleBuilder = this.handleCronScheduleMisfirePolicy(sysjob, cronScheduleBuilder);
				// 按照新的规则进行
				trigger = trigger.getTriggerBuilder()
					.withIdentity(triggerKey)
					.withSchedule(cronScheduleBuilder)
					.build();
				// 将任务信息更新到任务信息中
				trigger.getJobDataMap().put(BixiQuartzEnum.SCHEDULE_JOB_KEY.getType(), sysjob);
				// 重启
				scheduler.rescheduleJob(triggerKey, trigger);
			}
			// 如任务状态为暂停
			if (sysjob.getStatus().equals(BixiQuartzEnum.JOB_STATUS_NOT_RUNNING.getType())) {
				this.pauseJob(sysjob, scheduler);
			}
		}
		catch (ObjectAlreadyExistsException race) {
			// Another scheduler thread/node may have created the same identity after
			// our existence check. Re-read the trigger and let the caller trigger the
			// already-registered job instead of surfacing a duplicate-key failure.
			if (!reuseExistingJob(sysjob, scheduler)) throw schedulerFailure("添加或更新定时任务", race);
		}
		catch (SchedulerException e) {
			// JDBC job stores may wrap the same race as JobPersistenceException
			// rather than ObjectAlreadyExistsException.
			if (isDuplicateScheduleRace(e) && reuseExistingJob(sysjob, scheduler)) return;
			throw schedulerFailure("添加或更新定时任务", e);
		}
	}

	private boolean reuseExistingJob(SysJob sysjob, Scheduler scheduler) {
		for (int attempt = 0; attempt < 20; attempt++) {
			try {
				if (scheduler.getTrigger(getTriggerKey(sysjob)) != null) return true;
				if (attempt < 19) Thread.sleep(25L);
			}
			catch (SchedulerException verificationFailure) {
				throw schedulerFailure("添加或更新定时任务", verificationFailure);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return false;
	}

	private boolean isDuplicateScheduleRace(SchedulerException failure) {
		Throwable current = failure;
		while (current != null) {
			String message = current.getMessage();
			if (current instanceof java.sql.SQLIntegrityConstraintViolationException
					&& message != null && message.contains("Duplicate entry")) {
				return true;
			}
			if (message != null && message.contains("Duplicate entry")
					&& (message.contains("QRTZ_JOB_DETAILS") || message.contains("QRTZ_TRIGGERS"))) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	/**
	 * 立即执行一次任务
	 */
	public static boolean runOnce(Scheduler scheduler, SysJob sysJob) {
		try {
			// 参数
			JobDataMap dataMap = new JobDataMap();
			dataMap.put(BixiQuartzEnum.SCHEDULE_JOB_KEY.getType(), sysJob);

			scheduler.triggerJob(getKey(sysJob), dataMap);
		}
		catch (SchedulerException e) {
			log.error("立刻执行定时任务，失败信息：{}", e.getMessage());
			return false;
		}

		return true;
	}

	/**
	 * 暂停定时任务
	 * @param sysjob
	 * @param scheduler
	 */
	public void pauseJob(SysJob sysjob, Scheduler scheduler) {
		try {
			if (scheduler != null) {
				scheduler.pauseJob(getKey(sysjob));
			}
		}
		catch (SchedulerException e) {
			throw schedulerFailure("暂停任务", e);
		}

	}

	/**
	 * 恢复定时任务
	 * @param sysjob
	 * @param scheduler
	 */
	public void resumeJob(SysJob sysjob, Scheduler scheduler) {
		try {
			if (scheduler != null) {
				scheduler.resumeJob(getKey(sysjob));
			}
		}
		catch (SchedulerException e) {
			throw schedulerFailure("恢复任务", e);
		}

	}

	/**
	 * 移除定时任务
	 * @param sysjob
	 * @param scheduler
	 */
	public void removeJob(SysJob sysjob, Scheduler scheduler) {
		try {
			if (scheduler != null) {
				removeJob(scheduler, getTriggerKey(sysjob), getKey(sysjob));
				removeLegacyJob(sysjob, scheduler);
			}
		}
		catch (SchedulerException e) {
			throw schedulerFailure("删除任务", e);
		}
	}

	private void removeLegacyJob(SysJob sysjob, Scheduler scheduler) throws SchedulerException {
		TriggerKey legacyTriggerKey = TriggerKey.triggerKey(sysjob.getName(), sysjob.getGroup());
		if (scheduler.getTrigger(legacyTriggerKey) != null) {
			removeJob(scheduler, legacyTriggerKey, JobKey.jobKey(sysjob.getName(), sysjob.getGroup()));
		}
	}

	private void removeJob(Scheduler scheduler, TriggerKey triggerKey, JobKey jobKey) throws SchedulerException {
		// Quartz throws when pause/unschedule targets an identity already removed by
		// another node. Treat that state as an idempotent delete while still
		// removing an orphaned job detail when no trigger remains.
		if (scheduler.getTrigger(triggerKey) != null) {
			scheduler.pauseTrigger(triggerKey);
			scheduler.unscheduleJob(triggerKey);
			scheduler.deleteJob(jobKey);
		}
		else if (scheduler.checkExists(jobKey)) {
			scheduler.deleteJob(jobKey);
		}
	}

	/**
	 * 启动所有运行定时任务
	 * @param scheduler
	 */
	public void startJobs(Scheduler scheduler) {
		try {
			if (scheduler != null) {
				scheduler.resumeAll();
			}
		}
		catch (SchedulerException e) {
			throw schedulerFailure("启动全部任务", e);
		}
	}

	/**
	 * 停止所有运行定时任务
	 * @param scheduler
	 */
	public void pauseJobs(Scheduler scheduler) {
		try {
			if (scheduler != null) {
				scheduler.pauseAll();
			}
		}
		catch (SchedulerException e) {
			throw schedulerFailure("暂停全部任务", e);
		}
	}

	private IllegalStateException schedulerFailure(String action, SchedulerException failure) {
		log.error("{}失败", action, failure);
		return new IllegalStateException(action + "失败: " + failure.getMessage(), failure);
	}

	/**
	 * 获取错失执行策略方法
	 * @param sysJob
	 * @param cronScheduleBuilder
	 * @return
	 */
	private CronScheduleBuilder handleCronScheduleMisfirePolicy(SysJob sysJob,
			CronScheduleBuilder cronScheduleBuilder) {
		if (BixiQuartzEnum.MISFIRE_DEFAULT.getType().equals(sysJob.getMisfirePolicy())) {
			return cronScheduleBuilder;
		}
		else if (BixiQuartzEnum.MISFIRE_IGNORE_MISFIRES.getType().equals(sysJob.getMisfirePolicy())) {
			return cronScheduleBuilder.withMisfireHandlingInstructionIgnoreMisfires();
		}
		else if (BixiQuartzEnum.MISFIRE_FIRE_AND_PROCEED.getType().equals(sysJob.getMisfirePolicy())) {
			return cronScheduleBuilder.withMisfireHandlingInstructionFireAndProceed();
		}
		else if (BixiQuartzEnum.MISFIRE_DO_NOTHING.getType().equals(sysJob.getMisfirePolicy())) {
			return cronScheduleBuilder.withMisfireHandlingInstructionDoNothing();
		}
		else {
			return cronScheduleBuilder;
		}
	}

	/**
	 * 判断cron表达式是否正确
	 * @param cronExpression
	 * @return
	 */
	public boolean isValidCron(String cronExpression) {
		return CronExpression.isValidExpression(cronExpression);
	}

}
