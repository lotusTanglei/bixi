package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务提醒表
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@TableName("script_task_reminder")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "任务提醒表")
public class ScriptTaskReminder extends BaseEntity<ScriptTaskReminder> {

    private static final long serialVersionUID = 1L;

    /**
     * 提醒ID
     */
    @TableId
    @Schema(description = "提醒ID")
    private Long id;

    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    private Long taskId;

    /**
     * 提醒时间
     */
    @Schema(description = "提醒时间")
    private LocalDateTime remindTime;

    /**
     * 提醒渠道（email、sms、message）
     */
    @Schema(description = "提醒渠道（email、sms、message）")
    private String channel;

    /**
     * 发送状态（0待发送 1已发送 2发送失败）
     */
    @Schema(description = "发送状态（0待发送 1已发送 2发送失败）")
    private String status;

    /**
     * 提醒标题
     */
    @Schema(description = "提醒标题")
    private String title;

    /**
     * 提醒内容
     */
    @Schema(description = "提醒内容")
    private String content;

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
