package com.lotus.bixi.workflow.mapper;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@ConditionalOnWorkflowEnabled
@Mapper
public interface WfFormVersionMapper extends BaseMapper<WfFormVersion> {

    @Select("SELECT COALESCE(MAX(version), 0) FROM wf_form_version WHERE form_id = #{formId} AND del_flag = '0'")
    int selectMaxVersion(@Param("formId") Long formId);
}
