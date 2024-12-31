package com.lotus.bixi.quartz.config;

import com.lotus.bixi.quartz.constants.BixiQuartzEnum;
import com.lotus.bixi.quartz.entity.SysJob;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author 唐磊
 *
 * <p>
 * 动态任务工厂
 */
@Slf4j
@DisallowConcurrentExecution
public class BixiQuartzFactory implements Job {

	@Autowired
	private BixiQuartzInvokeFactory quartzInvokeFactory;

	@Override
	@SneakyThrows
	public void execute(JobExecutionContext jobExecutionContext) {
		SysJob sysJob = (SysJob) jobExecutionContext.getMergedJobDataMap()
			.get(BixiQuartzEnum.SCHEDULE_JOB_KEY.getType());
		quartzInvokeFactory.init(sysJob, jobExecutionContext.getTrigger());
	}

}
