package com.lotus.bixi.upms.demo.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
@ConditionalOnWorkflowEnabled
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> {
    /** Serialize automatic-task state transitions on the leave aggregate. */
    @Select("SELECT * FROM demo_leave_request WHERE id = #{id} FOR UPDATE")
    LeaveRequest selectByIdForUpdate(@Param("id") Long id);
}
