package com.lotus.bixi.quartz.event;

import com.lotus.bixi.quartz.entity.SysJob;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.quartz.Trigger;

/**
 * @author 唐磊 定时任务多线程事件
 */
@Getter
@AllArgsConstructor
public class SysJobEvent {

	private final SysJob sysJob;

	private final Trigger trigger;

}
