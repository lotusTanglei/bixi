package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "角色字段权限批量配置")
public class FormFieldPermissionBatchDTO implements Serializable {

    @Pattern(regexp = "[A-Za-z0-9_.:-]{1,64}", message = "流程定义ID格式不正确")
    @Schema(description = "Flowable流程定义ID，为空时适用于全部流程定义")
    private String processDefinitionId;

    @Pattern(regexp = "(?:__start__|[A-Za-z][A-Za-z0-9_.-]{0,63})", message = "任务定义Key格式不正确")
    @Schema(description = "任务定义Key，为空时适用于全部节点")
    private String taskDefinitionKey;

    @Valid
    @NotEmpty(message = "字段权限不能为空")
    @Schema(description = "完整字段权限列表")
    private List<FormFieldPermissionItemDTO> fields;

    private static final long serialVersionUID = 1L;
}
