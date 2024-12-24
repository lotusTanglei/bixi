
package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.generator.entity.GenFieldType;
import com.lotus.bixi.generator.mapper.GenFieldTypeMapper;
import com.lotus.bixi.generator.service.GenFieldTypeService;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 列属性
 *
 * @author tanglei
 * @date 2023-02-06 20:16:01
 */
@Service
public class GenFieldTypeServiceImpl extends ServiceImpl<GenFieldTypeMapper, GenFieldType>
		implements GenFieldTypeService {

	/**
	 * 根据tableId，获取包列表
	 * @param dsName 数据源名称
	 * @param tableName 表名称
	 * @return 返回包列表
	 */
	@Override
	public Set<String> getPackageByTableId(String dsName, String tableName) {
		Set<String> importList = baseMapper.getPackageByTableId(dsName, tableName);
		return importList.stream().filter(StrUtil::isNotBlank).collect(Collectors.toSet());
	}

}
