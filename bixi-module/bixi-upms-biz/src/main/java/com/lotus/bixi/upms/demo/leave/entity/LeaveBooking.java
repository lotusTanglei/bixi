package com.lotus.bixi.upms.demo.leave.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable local side effect for an approved leave workflow operation. */
@Data
@TableName("demo_leave_booking")
public class LeaveBooking {
    @TableId(value = "operation_id", type = IdType.INPUT)
    private String operationId;
    @TableField("leave_id")
    private Long leaveId;
    private Integer round;
    @TableField("request_hash")
    private String requestHash;
    @TableField("booking_state")
    private String bookingState;
    @TableField("booking_reference")
    private String bookingReference;
    @TableField("compensation_id")
    private String compensationId;
    @TableField("tenant_id")
    private Long tenantId;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
