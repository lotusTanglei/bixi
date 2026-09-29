package com.lotus.bixi.quartz.entity;

import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 定时任务执行日志表
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "定时任务日志")
public class SysJobRecord extends BaseEntity<SysJobRecord> {

	/**
	 * 任务id
	 */
	private Long jobId;

	/**
	 * Quartz 单次触发的执行标识，同一次触发的重试共享该值
	 */
	private String executionId;

	/**
	 * 当前尝试序号，从 1 开始
	 */
	private Integer attempt;

	/**
	 * 本次触发允许的最大尝试次数
	 */
	private Integer maxAttempts;

	/**
	 * CRON、MANUAL 或 RECOVERY
	 */
	private String triggerType;

	/**
	 * 是否由 Quartz 节点故障恢复触发
	 */
	private Boolean recovered;

	/**
	 * 日志信息
	 */
	private String message;

	/**
	 * 执行时间
	 */
	private String executeTime;

	/**
	 * 异常信息
	 */
	private String exceptionInfo;


}
