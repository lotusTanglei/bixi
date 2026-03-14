package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "表单定义展示对象")
public class FormVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "表单标识")
    private String formKey;

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
