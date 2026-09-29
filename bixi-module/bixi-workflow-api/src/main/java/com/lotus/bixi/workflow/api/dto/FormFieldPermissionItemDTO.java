package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "字段权限配置项")
public class FormFieldPermissionItemDTO implements Serializable {

    @NotBlank(message = "字段编码不能为空")
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}", message = "字段编码格式不正确")
    @Schema(description = "字段编码")
    private String fieldCode;

    @NotBlank(message = "字段权限不能为空")
    @Pattern(regexp = "edit|readonly|hidden", message = "字段权限必须是 edit、readonly 或 hidden")
    @Schema(description = "字段权限：edit/readonly/hidden")
    private String permType;

    private static final long serialVersionUID = 1L;
}
