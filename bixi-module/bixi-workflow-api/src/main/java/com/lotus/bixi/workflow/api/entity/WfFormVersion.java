package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form_version")
@Schema(description = "表单版本表")
public class WfFormVersion extends BaseEntity<WfFormVersion> {

    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "表单Schema JSON")
    private String schemaJson;

    @Schema(description = "变更日志")
    private String changeLog;

    @Schema(description = "是否激活版本 0:否 1:是")
    private String isActive;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
