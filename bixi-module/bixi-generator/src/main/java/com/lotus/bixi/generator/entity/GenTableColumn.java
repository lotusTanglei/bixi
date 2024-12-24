

package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author tanglei
 * @date 2023-02-06
 *
 * 记录表字段的配置信息
 */
@Data
@TableName("gen_table_column")
@EqualsAndHashCode(callSuper = true)
public class GenTableColumn extends BaseEntity<GenDatasourceConfig> {

	/**
	 * 数据源名
	 */
	private String dsName;

	/**
	 * 表名称
	 */
	private String tableName;

	/**
	 * 字段名称
	 */
	private String fieldName;

	/**
	 * 排序
	 */
	private Integer sort;

	/**
	 * 字段类型
	 */
	private String fieldType;

	/**
	 * 字段说明
	 */
	private String fieldComment;

	/**
	 * 属性名
	 */
	private String attrName;

	/**
	 * 属性类型
	 */
	private String attrType;

	/**
	 * 属性包名
	 */
	private String packageName;

	/**
	 * 自动填充
	 */
	private String autoFill;

	/**
	 * 主键 0：否 1：是
	 */
	private String primaryPk;

	/**
	 * 基类字段 0：否 1：是
	 */
	private String baseField;

	/**
	 * 表单项 0：否 1：是
	 */
	private String formItem;

	/**
	 * 表单必填 0：否 1：是
	 */
	private String formRequired;

	/**
	 * 表单类型
	 */
	private String formType;

	/**
	 * 表单效验
	 */
	private String formValidator;

	/**
	 * 列表项 0：否 1：是
	 */
	private String gridItem;

	/**
	 * 列表排序 0：否 1：是
	 */
	private String gridSort;

	/**
	 * 查询项 0：否 1：是
	 */
	private String queryItem;

	/**
	 * 查询方式
	 */
	private String queryType;

	/**
	 * 查询表单类型
	 */
	private String queryFormType;

	/**
	 * 字段字典类型
	 */
	@TableField(updateStrategy = FieldStrategy.IGNORED)
	private String fieldDict;

}
