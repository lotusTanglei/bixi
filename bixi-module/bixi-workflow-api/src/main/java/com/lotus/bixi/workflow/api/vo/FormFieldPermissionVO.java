package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "字段权限视图对象")
public class FormFieldPermissionVO implements Serializable {

    @Schema(description = "字段编码")
    private String fieldCode;

    @Schema(description = "字段标签")
    private String fieldLabel;

    @Schema(description = "权限类型")
    private String permType;

    private static final long serialVersionUID = 1L;
}
