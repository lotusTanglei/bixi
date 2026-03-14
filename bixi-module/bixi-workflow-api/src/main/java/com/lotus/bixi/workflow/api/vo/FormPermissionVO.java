package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "表单权限展示对象")
public class FormPermissionVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "字段编码，为空表示表单级权限")
    private String fieldCode;

    @Schema(description = "权限标识")
    private String permission;

    @Schema(description = "权限类型：view/edit/readonly/hidden")
    private String permType;

    @Schema(description = "权限描述")
    private String description;

    @Schema(description = "创建人")
    private Long createBy;

    @Schema(description = "更新人")
    private Long updateBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "备注")
    private String remark;

    private static final long serialVersionUID = 1L;
}
