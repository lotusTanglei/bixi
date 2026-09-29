package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_form_permission")
@Schema(description = "表单权限配置表")
public class SysFormPermission extends BaseEntity<SysFormPermission> {

    @Schema(description = "表单ID")
    private Long formId;

    @TableField("form_version_id")
    @Schema(description = "表单版本ID，为空时适用于该表单全部版本")
    private Long formVersionId;

    @TableField("process_definition_id")
    @Schema(description = "Flowable流程定义ID，为空时适用于全部流程定义")
    private String processDefinitionId;

    @TableField("task_definition_key")
    @Schema(description = "任务定义Key，__start__ 表示发起节点，为空时适用于全部节点")
    private String taskDefinitionKey;

    @TableField("field_code")
    @Schema(description = "字段编码")
    private String fieldCode;

    @TableField("permission")
    @Schema(description = "权限标识")
    private String permission;

    @Schema(description = "权限类型 read:只读 write:可写 hide:隐藏")
    private String permType;

    @TableField("description")
    @Schema(description = "权限描述")
    private String description;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
