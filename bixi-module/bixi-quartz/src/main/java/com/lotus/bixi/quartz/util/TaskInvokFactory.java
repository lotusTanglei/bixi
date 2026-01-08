package com.lotus.bixi.quartz.util;

import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.quartz.constants.JobTypeQuartzEnum;
import com.lotus.bixi.quartz.exception.TaskException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author 唐磊
 * @version 1.0
 * @date 2025-01-01
 */
@Slf4j
public class TaskInvokFactory {

	/**
	 * 根据对应jobType获取对应 invoker
	 * @param jobType
	 * @return
	 * @throws TaskException
	 */
	public static TaskInvok getInvoker(String jobType) throws TaskException {
		if (StrUtil.isBlank(jobType)) {
			log.info("获取TaskInvok传递参数有误，jobType:{}", jobType);
			throw new TaskException("");
		}

		TaskInvok taskInvok = null;
		if (JobTypeQuartzEnum.JAVA.getType().equals(jobType)) {
			taskInvok = SpringContextHolder.getBean("javaClassTaskInvok");
		}
		else if (JobTypeQuartzEnum.SPRING_BEAN.getType().equals(jobType)) {
			taskInvok = SpringContextHolder.getBean("springBeanTaskInvok");
		}
		else if (JobTypeQuartzEnum.REST.getType().equals(jobType)) {
			taskInvok = SpringContextHolder.getBean("restTaskInvok");
		}
		else if (JobTypeQuartzEnum.JAR.getType().equals(jobType)) {
			taskInvok = SpringContextHolder.getBean("jarTaskInvok");
		}
		else if (StrUtil.isBlank(jobType)) {
			log.info("定时任务类型无对应反射方式，反射类型:{}", jobType);
			throw new TaskException("定时任务类型无对应反射方式，反射类型:" + jobType);
		}

		return taskInvok;
	}

}
