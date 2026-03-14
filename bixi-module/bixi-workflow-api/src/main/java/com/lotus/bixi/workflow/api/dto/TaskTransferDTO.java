package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "任务转办传输对象")
public class TaskTransferDTO implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @NotNull(message = "转办人ID不能为空")
    @Schema(description = "转办人ID")
    private Long transferUserId;

    @Schema(description = "转办人姓名")
    private String transferUserName;

    @Schema(description = "转办原因")
    private String transferReason;

    private static final long serialVersionUID = 1L;
}
