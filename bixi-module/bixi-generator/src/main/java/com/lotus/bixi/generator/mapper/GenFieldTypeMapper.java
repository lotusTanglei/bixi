

package com.lotus.bixi.generator.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.generator.entity.GenFieldType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;

/**
 * 列属性
 *
 * @author tanglei
 * @date 2023-02-06 20:16:01
 */
@Mapper
public interface GenFieldTypeMapper extends BaseMapper<GenFieldType> {

	/**
	 * 根据tableId，获取包列表
	 * @param dsName 数据源名称
	 * @param tableName 表名称
	 * @return 返回包列表
	 */
	Set<String> getPackageByTableId(@Param("dsName") String dsName, @Param("tableName") String tableName);

}
