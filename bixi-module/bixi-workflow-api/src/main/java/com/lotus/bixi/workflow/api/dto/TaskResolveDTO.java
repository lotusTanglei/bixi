package com.lotus.bixi.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/** A delegate reports their work; the original owner retains the approval decision. */
@Data
@EqualsAndHashCode(callSuper = true)
public class TaskResolveDTO extends WorkflowRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotBlank(message = "处理意见不能为空")
    @Size(max = 1000, message = "处理意见不能超过1000字")
    private String comment;
}
