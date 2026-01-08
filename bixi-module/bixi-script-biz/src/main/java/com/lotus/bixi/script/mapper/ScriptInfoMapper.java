package com.lotus.bixi.script.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.script.api.entity.ScriptInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 脚本主信息表 Mapper 接口
 *
 * @author bixi
 * @date 2025-01-01
 */
@Mapper
public interface ScriptInfoMapper extends BaseMapper<ScriptInfo> {

}
