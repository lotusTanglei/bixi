package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_definition")
@Schema(description = "流程定义扩展表")
public class WfProcessDefinition extends BaseEntity<WfProcessDefinition> {

    @Schema(description = "Flowable流程定义ID")
    private String processDefinitionId;

    @Schema(description = "流程标识")
    private String processKey;

    @Schema(description = "流程名称")
    private String processName;

    @Schema(description = "流程分类")
    private String category;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "表单Key")
    private String formKey;

    @Schema(description = "流程图资源名")
    private String diagramResourceName;

    @Schema(description = "挂起状态: 1激活, 0挂起")
    private Integer suspensionState;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
