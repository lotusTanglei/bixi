package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "审批记录展示对象")
public class ApprovalRecordVO implements Serializable {

    @Schema(description = "id")
    private Long id;

    @Schema(description = "流程实例ID")
    private String processInstanceId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务Key")
    private String taskKey;

    @Schema(description = "审批类型: approve/reject/transfer/delegate")
    private String approvalType;

    @Schema(description = "审批人ID")
    private Long approvalUserId;

    @Schema(description = "审批人姓名")
    private String approvalUserName;

    @Schema(description = "审批意见")
    private String approvalComment;

    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    @Schema(description = "被委托人ID")
    private Long delegateUserId;

    @Schema(description = "被委托人姓名")
    private String delegateUserName;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
