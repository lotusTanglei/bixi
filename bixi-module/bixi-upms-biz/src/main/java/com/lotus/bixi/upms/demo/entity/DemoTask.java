package com.lotus.bixi.upms.demo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * Independent sample business task shared by cloud and single deployments.
 */
@Data
@TableName("biz_demo_task")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "示例任务")
public class DemoTask extends BaseEntity<DemoTask> {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 120, message = "任务标题不能超过120个字符")
    @Schema(description = "任务标题")
    private String title;

    @NotBlank(message = "负责人不能为空")
    @Size(max = 64, message = "负责人不能超过64个字符")
    @Schema(description = "负责人")
    private String assignee;

    @NotBlank(message = "优先级不能为空")
    @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "优先级必须是LOW、MEDIUM或HIGH")
    @Schema(description = "优先级：LOW、MEDIUM、HIGH")
    private String priority;

    @NotBlank(message = "任务状态不能为空")
    @Pattern(regexp = "TODO|IN_PROGRESS|DONE", message = "任务状态必须是TODO、IN_PROGRESS或DONE")
    @Schema(description = "任务状态：TODO、IN_PROGRESS、DONE")
    private String taskStatus;

    @NotNull(message = "截止日期不能为空")
    @Schema(description = "截止日期")
    private LocalDate dueDate;

}
