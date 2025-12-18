package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户消息关联表
 *
 * @author bixi
 * @date 2024-05-20
 */
@Data
@TableName("sys_user_notice")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户消息关联表")
public class SysUserNotice extends BaseEntity<SysUserNotice> {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 通知ID
     */
    @Schema(description = "通知ID")
    private Long noticeId;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private Long userId;

    /**
     * 是否已读（0否 1是）
     */
    @Schema(description = "是否已读（0否 1是）")
    private String isRead;

    /**
     * 阅读时间
     */
    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

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
