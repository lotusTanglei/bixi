package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "流程定义展示对象")
public class ProcessDefinitionVO implements Serializable {

    @Schema(description = "id")
    private Long id;

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

    @Schema(description = "部署ID")
    private String deploymentId;

    @Schema(description = "XML资源名")
    private String xmlResourceName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
