package com.lotus.bixi.workflow.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.lotus.bixi.workflow.api.json.WorkflowJsonVariables;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务完成传输对象")
public class TaskCompleteDTO extends WorkflowRequestDTO implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "审批意见")
    private String approvalComment;

    @Schema(description = "审批类型")
    private String approvalType;

    @Schema(description = "流程变量")
    @JsonSerialize(using = WorkflowJsonVariables.Serializer.class)
    @JsonDeserialize(using = WorkflowJsonVariables.Deserializer.class)
    private Map<String, Object> variables;

    @Schema(description = "表单数据JSON")
    private String formDataJson;

    @Schema(description = "表单ID")
    private Long formId;

    private static final long serialVersionUID = 1L;
}
