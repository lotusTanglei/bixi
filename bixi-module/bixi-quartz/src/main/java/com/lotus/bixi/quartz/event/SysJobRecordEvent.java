package com.lotus.bixi.quartz.event;

import com.lotus.bixi.quartz.entity.SysJobRecord;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author 唐磊 定时任务日志多线程事件
 */
@Getter
@AllArgsConstructor
public class SysJobRecordEvent {

	private final SysJobRecord sysJobRecord;

}
