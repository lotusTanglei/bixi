package com.lotus.bixi.upms.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "示例任务查询条件")
public class DemoTaskQuery {

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "负责人")
    private String assignee;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "任务状态")
    private String taskStatus;

}
