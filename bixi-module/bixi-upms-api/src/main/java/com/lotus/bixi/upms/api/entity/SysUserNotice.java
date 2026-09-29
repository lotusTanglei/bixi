package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseRelationEntity;
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
public class SysUserNotice extends BaseRelationEntity<SysUserNotice> {

    private static final long serialVersionUID = 1L;

    public static final String DELIVERY_PENDING = "PENDING";
    public static final String DELIVERY_IN_FLIGHT = "IN_FLIGHT";
    public static final String DELIVERY_DELIVERED = "DELIVERED";
    public static final String DELIVERY_FAILED = "FAILED";
    public static final String RECEIPT_PENDING = "PENDING";
    public static final String RECEIPT_DELIVERED = "DELIVERED";
    public static final String RECEIPT_FAILED = "FAILED";
    /** A worker lease older than this can be reclaimed after a process crash. */
    public static final long DELIVERY_LEASE_SECONDS = 300L;

    /**
     * ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Schema(description = "ID")
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
     * SSE delivery state for this recipient. Read state and delivery state are
     * intentionally separate: a recipient can receive a refresh and still
     * leave the notice unread.
     */
    @Schema(description = "实时投递状态")
    private String deliveryStatus;

    @Schema(description = "实时投递尝试次数")
    private Integer deliveryAttempts;

    @Schema(description = "最近一次投递失败原因")
    private String deliveryLastError;

    @Schema(description = "最近一次投递尝试时间")
    private LocalDateTime deliveryLastAttemptAt;

    @Schema(description = "投递成功时间")
    private LocalDateTime deliveryDeliveredAt;

    /** Provider acknowledged status, separate from the initial HTTP send result. */
    @Schema(description = "第三方回执状态（PENDING、DELIVERED、FAILED）")
    private String deliveryReceiptStatus;

    /** Bounded provider code; arbitrary callback payloads are never persisted. */
    @Schema(description = "第三方回执码（已脱敏）")
    private String deliveryReceiptCode;

    @Schema(description = "第三方回执时间")
    private LocalDateTime deliveryReceiptAt;
}
