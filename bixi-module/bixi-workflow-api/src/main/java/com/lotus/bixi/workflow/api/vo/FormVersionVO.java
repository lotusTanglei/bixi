package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "表单版本展示对象")
public class FormVersionVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

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
