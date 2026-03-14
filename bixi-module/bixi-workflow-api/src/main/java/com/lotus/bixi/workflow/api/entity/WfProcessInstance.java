package com.lotus.bixi.workflow.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance")
@Schema(description = "流程实例扩展表")
public class WfProcessInstance extends BaseEntity<WfProcessInstance> {

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
