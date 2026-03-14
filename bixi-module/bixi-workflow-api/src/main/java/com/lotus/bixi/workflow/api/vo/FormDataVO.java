package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "表单数据展示对象")
public class FormDataVO implements Serializable {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "表单ID")
    private Long formId;

    @Schema(description = "表单版本ID")
    private Long formVersionId;

    @Schema(description = "流程实例ID")
    private String processInstanceId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "表单数据JSON")
    private String dataJson;

    @Schema(description = "提交人ID")
    private Long submitUserId;

    @Schema(description = "提交人姓名")
    private String submitUserName;

    @Schema(description = "提交时间")
    private LocalDateTime submitTime;

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
