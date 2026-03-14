package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
@Schema(description = "表单渲染视图对象")
public class FormRenderVO implements Serializable {

    @Schema(description = "表单标识")
    private String formKey;

    @Schema(description = "表单名称")
    private String formName;

    @Schema(description = "表单JSON Schema")
    private String schemaJson;

    @Schema(description = "表单数据JSON")
    private String dataJson;

    @Schema(description = "字段权限映射")
    private Map<String, String> permissions;

    @Schema(description = "版本号")
    private Integer version;

    private static final long serialVersionUID = 1L;
}
