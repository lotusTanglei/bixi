package com.lotus.bixi.quartz.util;

import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.exception.TaskException;

/**
 * 定时任务反射实现接口类
 *
 * @author 唐磊
 */
public interface TaskInvok {

	/**
	 * 执行反射方法
	 * @param sysJob 配置类
	 * @throws TaskException
	 */
	void invokMethod(SysJob sysJob) throws TaskException;

}
