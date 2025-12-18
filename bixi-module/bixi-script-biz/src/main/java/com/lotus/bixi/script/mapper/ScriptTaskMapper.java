package com.lotus.bixi.script.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.script.api.entity.ScriptTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务表 Mapper 接口
 *
 * @author bixi
 * @date 2024-05-20
 */
@Mapper
public interface ScriptTaskMapper extends BaseMapper<ScriptTask> {

}
