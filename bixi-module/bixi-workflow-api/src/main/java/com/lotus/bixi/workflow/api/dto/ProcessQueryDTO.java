package com.lotus.bixi.workflow.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "流程查询传输对象")
public class ProcessQueryDTO implements Serializable {

    @Schema(description = "流程标识")
    private String processKey;

    @Schema(description = "流程名称")
    private String processName;

    @Schema(description = "业务Key")
    private String businessKey;

    @Schema(description = "流程状态")
    private String status;

    @Schema(description = "发起人ID")
    private Long startUserId;

    @Schema(description = "开始时间起")
    private LocalDateTime startTimeBegin;

    @Schema(description = "开始时间止")
    private LocalDateTime startTimeEnd;

    private static final long serialVersionUID = 1L;
}
