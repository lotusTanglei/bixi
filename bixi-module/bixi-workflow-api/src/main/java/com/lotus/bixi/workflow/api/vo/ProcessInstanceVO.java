package com.lotus.bixi.workflow.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "流程实例展示对象")
public class ProcessInstanceVO implements Serializable {

    @Schema(description = "id")
    private Long id;

    @Schema(description = "Flowable流程实例ID")
    private String processInstanceId;

    @Schema(description = "流程定义ID")
    private String processDefinitionId;

    @Schema(description = "流程标识")
    private String processKey;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "业务表名")
    private String businessTable;

    @Schema(description = "业务ID")
    private Long businessId;

    @Schema(description = "流程标题")
    private String title;

    @Schema(description = "发起人ID")
    private Long startUserId;

    @Schema(description = "发起人姓名")
    private String startUserName;

    @Schema(description = "流程状态: running/completed/terminated")
    private String status;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "耗时毫秒")
    private Long duration;

    @Schema(description = "当前节点名称")
    private String currentActivityName;

    @Schema(description = "当前处理人")
    private String currentAssignee;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
