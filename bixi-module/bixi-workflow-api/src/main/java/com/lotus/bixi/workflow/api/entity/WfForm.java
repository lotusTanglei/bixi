package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form")
@Schema(description = "表单定义表")
public class WfForm extends BaseEntity<WfForm> {

    @Schema(description = "表单唯一标识")
    private String formKey;

    @Schema(description = "表单名称")
    private String formName;

    @Schema(description = "表单描述")
    private String formDesc;

    @Schema(description = "分类ID")
    private Long categoryId;

    @Schema(description = "当前版本号")
    private Integer currentVersion;

    @Schema(description = "状态 0:禁用 1:启用")
    private String status;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
