package com.lotus.bixi.upms.demo.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_leave_request")
public class LeaveRequest extends BaseEntity<LeaveRequest> {
    private Long applicantId;
    private Long approverId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private String leaveStatus;
    private String businessKey;
    private Integer round;
    private String processInstanceId;
    private String startCommandId;
    private String startRequestHash;
    private LocalDateTime submittedAt;
    private LocalDateTime endedAt;
}
