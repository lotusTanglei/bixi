package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "任务展示对象")
public class TaskVO implements Serializable {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务Key")
    private String taskKey;

    @Schema(description = "流程实例ID")
    private String processInstanceId;

    @Schema(description = "流程定义ID")
    private String processDefinitionId;

    @Schema(description = "流程标识")
    private String processKey;

    @Schema(description = "流程名称")
    private String processName;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "处理人")
    private String assignee;

    @Schema(description = "处理人姓名")
    private String assigneeName;

    @Schema(description = "拥有者")
    private String owner;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "到期时间")
    private LocalDateTime dueDate;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "表单Key")
    private String formKey;

    private static final long serialVersionUID = 1L;
}
