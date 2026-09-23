package com.lotus.bixi.upms.demo.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.demo.leave.entity.LeaveBooking;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
@ConditionalOnWorkflowEnabled
public interface LeaveBookingMapper extends BaseMapper<LeaveBooking> {
    @Select("SELECT * FROM demo_leave_booking WHERE operation_id = #{operationId} FOR UPDATE")
    LeaveBooking selectByOperationIdForUpdate(@Param("operationId") String operationId);

    @Select("SELECT * FROM demo_leave_booking WHERE leave_id = #{leaveId} AND round = #{round} FOR UPDATE")
    LeaveBooking selectByLeaveRoundForUpdate(@Param("leaveId") Long leaveId, @Param("round") Integer round);
}
