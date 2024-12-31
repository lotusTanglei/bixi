package com.lotus.bixi.quartz.util;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.lotus.bixi.quartz.entity.SysJob;
import com.lotus.bixi.quartz.exception.TaskException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 定时任务rest反射实现
 *
 * @author 唐磊
 */
@Slf4j
@AllArgsConstructor
@Component("restTaskInvok")
public class RestTaskInvok implements TaskInvok {

	@Override
	public void invokMethod(SysJob sysJob) throws TaskException {
		try {
			HttpRequest request = HttpUtil.createGet(sysJob.getExecutePath());
			request.execute();
		}
		catch (Exception e) {
			log.error("定时任务restTaskInvok异常,执行任务：{}", sysJob.getExecutePath());
			throw new TaskException("定时任务restTaskInvok业务执行失败,任务：" + sysJob.getExecutePath());
		}
	}

}
