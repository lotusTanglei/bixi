package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "任务评论传输对象")
public class TaskCommentDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @NotBlank(message = "评论内容不能为空")
    @Schema(description = "评论内容")
    private String message;

    @Schema(description = "评论类型")
    private String type;

}
