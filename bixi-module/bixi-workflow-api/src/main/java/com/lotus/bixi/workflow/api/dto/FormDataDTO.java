package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "表单数据传输对象")
public class FormDataDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "表单ID不能为空")
    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "表单版本ID")
    private Long formVersionId;

    @Schema(description = "流程实例ID")
    private String processInstanceId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "表单数据JSON")
    private String dataJson;

    @Schema(description = "提交人ID")
    private Long submitUserId;

    @Schema(description = "提交人姓名")
    private String submitUserName;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

    @Schema(description = "备注")
    private String remark;

    private static final long serialVersionUID = 1L;
}
