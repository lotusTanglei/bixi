package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源表
 *
 * @author tanglei
 * @date 2025-01-01
 */
@Mapper
public interface GenDatasourceConfigMapper extends BaseMapper<GenDatasourceConfig> {
}