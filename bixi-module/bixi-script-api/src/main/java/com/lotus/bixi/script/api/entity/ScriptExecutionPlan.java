package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 脚本执行计划表
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@TableName("script_execution_plan")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "脚本执行计划表")
public class ScriptExecutionPlan extends BaseEntity<ScriptExecutionPlan> {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId
    @Schema(description = "主键ID")
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
     * 计划状态（0待计划 1已计划 2已执行 3已取消）
     */
    @Schema(description = "计划状态（0待计划 1已计划 2已执行 3已取消）")
    private String planStatus;

    /**
     * 执行顺序
     */
    @Schema(description = "执行顺序")
    private Integer execOrder;

    /**
     * 计划执行时间
     */
    @Schema(description = "计划执行时间")
    private LocalDateTime scheduleTime;

    /**
     * 优先级
     */
    @Schema(description = "优先级")
    private Integer priority;

    /**
     * 状态（0有效 1无效）
     */
    @Schema(description = "状态（0有效 1无效）")
    private String status;

    /**
     * 数据状态
     */
    @Schema(description = "数据状态")
    private String dataStatus;

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
