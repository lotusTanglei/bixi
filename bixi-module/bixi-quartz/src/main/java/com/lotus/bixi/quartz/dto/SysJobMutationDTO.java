package com.lotus.bixi.quartz.dto;

import com.lotus.bixi.quartz.entity.SysJob;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.quartz.CronExpression;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysJobMutationDTO {

	@Positive(message = "任务ID必须为正数")
	private Long id;

	@NotBlank(message = "任务名称不能为空")
	@Size(max = 64, message = "任务名称不能超过64个字符")
	private String name;

	@NotBlank(message = "任务组不能为空")
	@Size(max = 64, message = "任务组不能超过64个字符")
	private String group;

	@Builder.Default
	@Pattern(regexp = "[1-9]", message = "任务优先级必须在1到9之间")
	private String order = "1";

	@NotBlank(message = "任务类型不能为空")
	@Pattern(regexp = "[1-4]", message = "任务类型必须是1到4")
	private String type;

	@Size(max = 500, message = "执行路径不能超过500个字符")
	private String executePath;

	@Size(max = 500, message = "执行类或Bean名称不能超过500个字符")
	private String className;

	@Size(max = 500, message = "执行方法不能超过500个字符")
	private String methodName;

	@Size(max = 2000, message = "任务参数不能超过2000个字符")
	private String methodParamsValue;

	@NotBlank(message = "cron表达式不能为空")
	@Size(max = 255, message = "cron表达式不能超过255个字符")
	private String cronExpression;

	@Builder.Default
	@NotBlank(message = "错失执行策略不能为空")
	@Pattern(regexp = "[0-3]", message = "错失执行策略必须是0到3")
	private String misfirePolicy = "3";

	@Builder.Default
	@NotNull(message = "重试次数不能为空")
	@Min(value = 0, message = "重试次数不能小于0")
	@Max(value = 5, message = "重试次数不能超过5")
	private Integer retryCount = 0;

	@Builder.Default
	@NotNull(message = "重试间隔不能为空")
	@Min(value = 1, message = "重试间隔不能小于1秒")
	@Max(value = 300, message = "重试间隔不能超过300秒")
	private Integer retryIntervalSeconds = 5;

	@Builder.Default
	@Pattern(regexp = "[12]", message = "租户任务类型必须是1或2")
	private String tenantType = "1";

	@Size(max = 500, message = "备注不能超过500个字符")
	private String remark;

	@AssertTrue(message = "cron表达式无效")
	public boolean isCronExpressionValid() {
		return cronExpression != null && CronExpression.isValidExpression(cronExpression);
	}

	@AssertTrue(message = "任务执行目标不完整")
	public boolean isInvocationTargetValid() {
		if ("1".equals(type) || "2".equals(type)) {
			return hasText(className) && hasText(methodName);
		}
		if ("3".equals(type) || "4".equals(type)) {
			return hasText(executePath);
		}
		return false;
	}

	public SysJob toEntity() {
		SysJob job = SysJob.builder()
			.name(name)
			.group(group)
			.order(order)
			.type(type)
			.executePath(executePath)
			.className(className)
			.methodName(methodName)
			.methodParamsValue(methodParamsValue)
			.cronExpression(cronExpression)
			.misfirePolicy(misfirePolicy)
			.retryCount(retryCount)
			.retryIntervalSeconds(retryIntervalSeconds)
			.tenantType(tenantType)
			.build();
		job.setId(id);
		job.setRemark(remark);
		return job;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
