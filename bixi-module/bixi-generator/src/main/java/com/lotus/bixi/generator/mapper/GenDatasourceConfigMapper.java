
package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源表
 *
 * @author tanglei
 * @date 2019-03-31 16:00:20
 */
@Mapper
public interface GenDatasourceConfigMapper extends BaseMapper<GenDatasourceConfig> {
}