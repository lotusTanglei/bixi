package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@Schema(description = "流程启动传输对象")
public class ProcessStartDTO implements Serializable {

    @NotBlank(message = "流程标识不能为空")
    @Schema(description = "流程标识")
    private String processKey;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "业务表名")
    private String businessTable;

    @Schema(description = "业务ID")
    private Long businessId;

    @Schema(description = "流程标题")
    private String title;

    @Schema(description = "流程变量")
    private Map<String, Object> variables;

    @Schema(description = "表单数据JSON")
    private String formDataJson;

    @Schema(description = "表单ID")
    private Long formId;

    private static final long serialVersionUID = 1L;
}
