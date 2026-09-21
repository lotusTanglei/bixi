package com.lotus.bixi.upms.demo.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.upms.demo.leave.entity.LeaveRequest;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@ConditionalOnWorkflowEnabled
public interface LeaveRequestMapper extends BaseMapper<LeaveRequest> { }
