package com.lotus.bixi.quartz.exception;

/**
 * 定时任务异常
 *
 * @author 唐磊
 */
public class TaskException extends Exception {

	public TaskException() {
		super();
	}

	public TaskException(String msg) {
		super(msg);
	}

}
