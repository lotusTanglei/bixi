package com.lotus.bixi.quartz.config;

import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.event.SysJobEvent;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * @author 唐磊
 */
@Slf4j
@Aspect
@Service
@AllArgsConstructor
public class BixiQuartzInvokeFactory {

	private final ApplicationEventPublisher publisher;

	@SneakyThrows
	void init(SysJob sysJob, JobExecutionContext context) {
		publisher.publishEvent(new SysJobEvent(sysJob, context.getTrigger(), context.getFireInstanceId(),
			context.isRecovering()));
	}

}
