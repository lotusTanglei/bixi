package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "表单权限传输对象")
public class FormPermissionDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotNull(message = "表单ID不能为空")
    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "字段编码，为空表示表单级权限")
    private String fieldCode;

    @NotBlank(message = "权限标识不能为空")
    @Schema(description = "权限标识")
    private String permission;

    @NotBlank(message = "权限类型不能为空")
    @Schema(description = "权限类型：view/edit/readonly/hidden")
    private String permType;

    @Schema(description = "权限描述")
    private String description;

    @Schema(description = "备注")
    private String remark;

    private static final long serialVersionUID = 1L;
}
