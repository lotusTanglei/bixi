package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务表
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@TableName("script_task")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务表")
public class ScriptTask extends BaseEntity<ScriptTask> {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId
    @Schema(description = "任务ID")
    private Long id;

    /**
     * 任务标题
     */
    @Schema(description = "任务标题")
    private String title;

    /**
     * 关联计划ID（可选）
     */
    @Schema(description = "关联计划ID（可选）")
    private Long planId;

    /**
     * 脚本ID
     */
    @Schema(description = "脚本ID")
    private Long scriptId;

    /**
     * 现场ID
     */
    @Schema(description = "现场ID")
    private Long siteId;

    /**
     * 被指派人ID
     */
    @Schema(description = "被指派人ID")
    private Long assignedTo;

    /**
     * 指派人ID
     */
    @Schema(description = "指派人ID")
    private Long assignerId;

    /**
     * 任务角色（executor、reviewer）
     */
    @Schema(description = "任务角色（executor、reviewer）")
    private String roleType;

    /**
     * 截止时间
     */
    @Schema(description = "截止时间")
    private LocalDateTime dueTime;

    /**
     * 任务状态（0待处理 1进行中 2已完成 3阻塞 4已取消）
     */
    @Schema(description = "任务状态（0待处理 1进行中 2已完成 3阻塞 4已取消）")
    private String status;

    /**
     * 优先级
     */
    @Schema(description = "优先级")
    private Integer priority;

    /**
     * 开启提醒（0否 1是）
     */
    @Schema(description = "开启提醒（0否 1是）")
    private String remindEnable;

    /**
     * 租户id
     */
    @Schema(description = "租户id")
    private String tenantId;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

}
