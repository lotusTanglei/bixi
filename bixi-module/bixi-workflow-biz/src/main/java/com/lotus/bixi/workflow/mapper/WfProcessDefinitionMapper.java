package com.lotus.bixi.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WfProcessDefinitionMapper extends BaseMapper<WfProcessDefinition> {

}
