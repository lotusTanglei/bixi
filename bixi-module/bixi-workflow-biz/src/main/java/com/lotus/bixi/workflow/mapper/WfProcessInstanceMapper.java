package com.lotus.bixi.workflow.mapper;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@ConditionalOnWorkflowEnabled
@Mapper
public interface WfProcessInstanceMapper extends BaseMapper<WfProcessInstance> {

    @Select("SELECT * FROM wf_process_instance WHERE process_instance_id = #{processInstanceId} FOR UPDATE")
    WfProcessInstance selectForUpdate(String processInstanceId);

    @Select("SELECT * FROM wf_process_instance WHERE process_instance_id = #{processInstanceId}")
    WfProcessInstance selectByProcessInstanceId(String processInstanceId);

}
