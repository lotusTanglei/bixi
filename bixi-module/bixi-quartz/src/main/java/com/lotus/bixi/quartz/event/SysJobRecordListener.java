package com.lotus.bixi.quartz.event;

import com.lotus.bixi.quartz.entity.SysJobRecord;
import com.lotus.bixi.quartz.service.SysJobRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author 唐磊 异步监听定时任务日志事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysJobRecordListener {

	private final SysJobRecordService sysJobRecordService;

	@Async
	@Order
	@EventListener(SysJobRecordEvent.class)
	public void saveSysJobRecord(SysJobRecordEvent event) {
		SysJobRecord sysJobRecord = event.getSysJobRecord();
		sysJobRecordService.save(sysJobRecord);
		log.info("执行定时任务日志");
	}

}
