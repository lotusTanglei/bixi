

package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 列属性
 *
 * @author tanglei
 * @date 2023-02-06 20:34:55
 */
@Data
@TableName("gen_table")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "列属性")
public class GenTable extends BaseEntity<GenTable> {

	/**
	 * 数据源名称
	 */
	@Schema(description = "数据源名称")
	private String dsName;

	/**
	 * 数据源类型
	 */
	@Schema(description = "数据源类型")
	private String dbType;

	/**
	 * 表名
	 */
	@Schema(description = "表名")
	private String tableName;

	/**
	 * 类名
	 */
	@Schema(description = "类名")
	private String className;

	/**
	 * 说明
	 */
	@Schema(description = "说明")
	private String tableComment;

	/**
	 * 作者
	 */
	@Schema(description = "作者")
	private String author;

	/**
	 * 邮箱
	 */
	@Schema(description = "邮箱")
	private String email;

	/**
	 * 项目包名
	 */
	@Schema(description = "项目包名")
	private String packageName;

	/**
	 * 项目版本号
	 */
	@Schema(description = "项目版本号")
	private String version;

	/**
	 * 生成方式 0：zip压缩包 1：自定义目录
	 */
	@Schema(description = "生成方式  0：zip压缩包   1：自定义目录")
	private String generatorType;

	/**
	 * 后端生成路径
	 */
	@Schema(description = "后端生成路径")
	private String backendPath;

	/**
	 * 前端生成路径
	 */
	@Schema(description = "前端生成路径")
	private String frontendPath;

	/**
	 * 模块名
	 */
	@Schema(description = "模块名")
	private String moduleName;

	/**
	 * 功能名
	 */
	@Schema(description = "功能名")
	private String functionName;

	/**
	 * 表单布局 1：一列 2：两列
	 */
	@Schema(description = "表单布局  1：一列   2：两列")
	private Integer formLayout;

	/**
	 * 基类ID
	 */
	@Schema(description = "基类ID")
	private Long baseclassId;

	/**
	 * 代码生成风格
	 */
	private Long style;

	/**
	 * 子表名称
	 */
	private String childTableName;

	/**
	 * 主表关联键
	 */
	private String mainField;

	/**
	 * 子表关联键
	 */
	private String childField;

	/**
	 * 字段列表
	 */
	@TableField(exist = false)
	private List<GenTableColumn> fieldList;

	/**
	 * 子表字段列表
	 */
	@TableField(exist = false)
	private List<GenTableColumn> childFieldList;

	/**
	 * 代码风格（模版分组信息）
	 */
	@TableField(exist = false)
	private List<GenGroup> groupList;

}
