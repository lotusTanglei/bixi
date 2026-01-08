package com.lotus.bixi.script.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.script.api.entity.ScriptExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 脚本执行记录表 Mapper 接口
 *
 * @author bixi
 * @date 2025-01-01
 */
@Mapper
public interface ScriptExecutionLogMapper extends BaseMapper<ScriptExecutionLog> {

}
