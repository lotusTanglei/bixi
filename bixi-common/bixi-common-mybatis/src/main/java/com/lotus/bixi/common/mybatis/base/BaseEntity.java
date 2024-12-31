package com.lotus.bixi.common.mybatis.base;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 抽象实体
 *
 * @author 唐磊
 * @date 2021/8/9
 */
@Getter
@Setter
public abstract class BaseEntity<T extends Model<?>> extends Model<T> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "id")
    private Long id;
    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人")
    private Long createBy;

    /**
     * 修改人
     */
    @TableField(fill = FieldFill.UPDATE)
    @Schema(description = "修改人")
    private Long updateBy;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    /**
     * 是否删除 1：已删除 0：正常
     */
    @TableLogic
    @Schema(description = "删除标记,1:已删除,0:正常")
    @TableField(fill = FieldFill.INSERT)
    private String delFlag;

    /**
     * 数据状态标记（业务）,0:正常
     */
    @Schema(description = "数据状态标记（业务）,0:正常")
    @TableField(fill = FieldFill.INSERT)
    private String status;

    /**
     * 数据状态标记（数据库）,0:正常
     */
    @Schema(description = "数据状态标记（数据库）,0:正常")
    @TableField(fill = FieldFill.INSERT)
    private String dataStatus;

    /**
     * 租户ID
     */
    @Schema(description = "租户id")
    private String tenantId;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

}
