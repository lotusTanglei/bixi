/*
 *    Copyright (c) 2018-2025, lengleng All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the pig4cloud.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: lengleng (wangiegie@gmail.com)
 */

package com.lotus.bixi.generator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.generator.dto.GenTableImportResult;
import com.lotus.bixi.generator.entity.GenTable;
import org.anyline.metadata.Table;

import java.util.List;

/**
 * 列属性
 *
 * @author 唐磊x code generator
 * @date 2025-01-01
 */
public interface GenTableService extends IService<GenTable> {

	/**
	 * 查询对应数据源的表
	 * @param page 分页信息
	 * @param table 查询条件
	 * @return 表
	 */
	IPage queryTablePage(Page<Table> page, GenTable table);

	/**
	 * 查询表信息（列），然后插入到中间表中
	 * @param dsName 数据源
	 * @param tableName 表名
	 * @return GenTable
	 */
	GenTable queryOrBuildTable(String dsName, String tableName);

	/**
	 * 精确查询已导入的生成配置，不触发导入
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @return 已存在的生成配置，不存在时返回 null
	 */
	GenTable findConfiguredTable(String dsName, String tableName);

	/**
	 * 精确查询已导入的完整生成配置，不存在时返回 null，且绝不触发导入
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @return 包含字段和模板组的配置，不存在时返回 null
	 */
	GenTable findConfiguredTableDetails(String dsName, String tableName);

	/**
	 * 导入物理表并在创建时写入调用方归属标记
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @param author 归属标记
	 * @return 创建结果；已存在时不修改原配置
	 */
	GenTableImportResult importTable(String dsName, String tableName, String author);

	/**
	 * 将已导入的生成配置与物理表结构同步
	 * @param dsName 数据源名称
	 * @param tableName 表名
	 * @return 同步后的生成配置
	 */
	GenTable syncTable(String dsName, String tableName);

	/**
	 * 查询表ddl 语句
	 * @param dsName 数据源名称
	 * @param tableName 表名称
	 * @return ddl 语句
	 * @throws Exception
	 */
	String queryTableDdl(String dsName, String tableName) throws Exception;

	/**
	 * 查询数据源里面的全部表
	 * @param dsName 数据源名称
	 * @return table
	 */
	List<String> queryTableList(String dsName);

	/**
	 * 查询表的全部字段
	 * @param dsName 数据源
	 * @param tableName 表名称
	 * @return column
	 */
	List<String> queryTableColumn(String dsName, String tableName);

}
