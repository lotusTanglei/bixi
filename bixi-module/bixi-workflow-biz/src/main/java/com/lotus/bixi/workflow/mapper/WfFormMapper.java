package com.lotus.bixi.workflow.mapper;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.workflow.api.entity.WfForm;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

@ConditionalOnWorkflowEnabled
@Mapper
public interface WfFormMapper extends BaseMapper<WfForm> {

    @Select("SELECT * FROM wf_form WHERE id = #{id} AND del_flag = '0' FOR UPDATE")
    WfForm selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM wf_form WHERE form_key = #{formKey} AND del_flag = '0' FOR UPDATE")
    WfForm selectByKeyForUpdate(@Param("formKey") String formKey);
}
