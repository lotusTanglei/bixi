package com.lotus.bixi.quartz.util;

import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.event.SysJobRecordEvent;
import com.lotus.bixi.quartz.service.SysJobService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronTrigger;
import org.quartz.Trigger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Date;

/**
 * 定时任务反射工具类
 *
 * @author 唐磊
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskInvokUtil {

	private final ApplicationEventPublisher publisher;

	private final SysJobService sysJobService;

	@SneakyThrows
	public void invokMethod(SysJob sysJob, Trigger trigger) {

		// 执行开始时间
		long startTime;
		// 执行结束时间
		long endTime;
		// 获取执行开始时间
		startTime = System.currentTimeMillis();
		// 更新定时任务表内的状态、执行时间、上次执行时间、下次执行时间等信息
		SysJob updateSysjob = new SysJob();
		updateSysjob.setId(sysJob.getId());
		// 日志
		SysJobRecord sysJobRecord = new SysJobRecord();
		sysJobRecord.setJobId(sysJob.getId());
		try {
			// 执行任务
			TaskInvok iTaskInvok = TaskInvokFactory.getInvoker(sysJob.getType());
			// 确保租户上下文有值，使得当前线程中的多租户特性生效。
			iTaskInvok.invokMethod(sysJob);
			// 记录成功状态
			sysJobRecord.setMessage(BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getDescription());
			sysJobRecord.setStatus(BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getType());
			// 任务表信息更新
			updateSysjob.setExecuteStatus(BixiQuartzEnum.JOB_LOG_STATUS_SUCCESS.getType());
		}
		catch (Throwable e) {
			log.error("定时任务执行失败，任务名称：{}；任务组名：{}，cron执行表达式：{}，执行时间：{}", sysJob.getName(), sysJob.getGroup(),
					sysJob.getCronExpression(), new Date());
			// 记录失败状态
			sysJobRecord.setMessage(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getDescription());
			sysJobRecord.setStatus(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getType());
			sysJobRecord.setExceptionInfo(StrUtil.sub(e.getMessage(), 0, 2000));
			// 任务表信息更新
			updateSysjob.setExecuteStatus(BixiQuartzEnum.JOB_LOG_STATUS_FAIL.getType());
		}
		finally {
			// 记录执行时间 立刻执行使用的是simpleTeigger
			if (trigger instanceof CronTrigger) {
				updateSysjob
					.setStartTime(trigger.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
				updateSysjob.setPreviousTime(
						trigger.getPreviousFireTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
				updateSysjob.setNextTime(
						trigger.getNextFireTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
			}
			// 记录执行时长
			endTime = System.currentTimeMillis();
			sysJobRecord.setExecuteTime(String.valueOf(endTime - startTime));

			publisher.publishEvent(new SysJobRecordEvent(sysJobRecord));
			sysJobService.updateById(updateSysjob);
		}
	}

}
