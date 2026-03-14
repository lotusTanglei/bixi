package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "表单查询传输对象")
public class FormQueryDTO implements Serializable {

    @Schema(description = "表单标识")
    private String formKey;

    @Schema(description = "表单名称")
    private String formName;

    @Schema(description = "表单类型：normal/approval/dynamic")
    private String formType;

    @Schema(description = "分类")
    private String category;

    @Schema(description = "状态：0-草稿，1-已发布，2-已停用")
    private String status;

    private static final long serialVersionUID = 1L;
}
