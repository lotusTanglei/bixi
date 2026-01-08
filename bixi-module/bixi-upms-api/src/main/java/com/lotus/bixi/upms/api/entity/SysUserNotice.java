package com.lotus.bixi.upms.api.entity;

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
 * @date 2025-01-01
 */
@Data
@TableName("sys_user_notice")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户消息关联表")
public class SysUserNotice extends BaseEntity<SysUserNotice> {

    private static final long serialVersionUID = 1L;

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
}
