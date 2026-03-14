package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_form_data")
@Schema(description = "表单数据表")
public class WfFormData extends BaseEntity<WfFormData> {

    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "表单版本号")
    private Integer formVersion;

    @Schema(description = "流程实例ID")
    private String processInstanceId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "业务主键")
    private String businessKey;

    @Schema(description = "表单数据JSON")
    private String formDataJson;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
