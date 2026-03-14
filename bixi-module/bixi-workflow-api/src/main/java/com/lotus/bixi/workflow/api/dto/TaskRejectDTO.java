package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "任务驳回传输对象")
public class TaskRejectDTO implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @NotBlank(message = "驳回原因不能为空")
    @Schema(description = "驳回原因")
    private String rejectReason;

    @Schema(description = "目标节点ID")
    private String targetActivityId;

    private static final long serialVersionUID = 1L;
}
