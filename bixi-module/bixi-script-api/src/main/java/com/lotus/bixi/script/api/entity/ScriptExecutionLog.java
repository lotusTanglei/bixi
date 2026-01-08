package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 脚本执行记录表
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@TableName("script_execution_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "脚本执行记录表")
public class ScriptExecutionLog extends BaseEntity<ScriptExecutionLog> {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId
    @Schema(description = "记录ID")
    private Long id;

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
     * 关联任务ID
     */
    @Schema(description = "关联任务ID")
    private Long taskId;

    /**
     * 执行结果（0成功 1失败 2跳过 3部分成功）
     */
    @Schema(description = "执行结果（0成功 1失败 2跳过 3部分成功）")
    private String status;

    /**
     * 实际执行人ID
     */
    @Schema(description = "实际执行人ID")
    private Long executorId;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime finishTime;

    /**
     * 耗时（毫秒）
     */
    @Schema(description = "耗时（毫秒）")
    private Long durationMs;

    /**
     * 详细日志
     */
    @Schema(description = "详细日志")
    private String logContent;

    /**
     * 错误信息摘要
     */
    @Schema(description = "错误信息摘要")
    private String errorMsg;

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
