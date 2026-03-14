package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "表单版本传输对象")
public class FormVersionDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "表单ID不能为空")
    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "表单JSON Schema")
    private String schemaJson;

    @Schema(description = "变更日志")
    private String changeLog;

    @Schema(description = "是否激活版本：0-否，1-是")
    private Integer isActive;

    @Schema(description = "备注")
    private String remark;

    private static final long serialVersionUID = 1L;
}
