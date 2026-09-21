package com.lotus.bixi.workflow.mapper;

import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import org.apache.ibatis.annotations.Mapper;

@ConditionalOnWorkflowEnabled
@Mapper
public interface SysFormPermissionMapper extends BaseMapper<SysFormPermission> {

}
