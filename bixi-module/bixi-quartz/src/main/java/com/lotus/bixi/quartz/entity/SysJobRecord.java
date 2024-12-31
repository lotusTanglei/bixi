package com.lotus.bixi.quartz.entity;

import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 定时任务执行日志表
 *
 * @author 唐磊
 * @date 2019-01-27 13:40:20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "定时任务日志")
public class SysJobRecord extends BaseEntity<SysJobRecord> {

	/**
	 * 任务id
	 */
	private Long jobId;

	/**
	 * 日志信息
	 */
	private String message;

	/**
	 * 执行状态（0正常 1失败）
	 */
	private String status;

	/**
	 * 执行时间
	 */
	private String executeTime;

	/**
	 * 异常信息
	 */
	private String exceptionInfo;


}
