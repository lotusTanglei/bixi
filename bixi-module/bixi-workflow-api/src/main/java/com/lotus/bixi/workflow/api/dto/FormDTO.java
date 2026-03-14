package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "表单定义传输对象")
public class FormDTO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @NotBlank(message = "表单标识不能为空")
    @Schema(description = "表单标识")
    private String formKey;

    @NotBlank(message = "表单名称不能为空")
    @Schema(description = "表单名称")
    private String formName;

    @Schema(description = "表单类型：normal/approval/dynamic")
    private String formType;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "状态：0-草稿，1-已发布，2-已停用")
    private String status;

    @Schema(description = "当前版本号")
    private Integer currentVersion;

    @Schema(description = "备注")
    private String remark;

    private static final long serialVersionUID = 1L;
}
