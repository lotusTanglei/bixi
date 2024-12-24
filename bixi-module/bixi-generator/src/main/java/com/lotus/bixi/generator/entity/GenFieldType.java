

package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 列属性
 *
 * @author tanglei
 * @date 2023-02-06 20:16:01
 */
@Data
@TableName("gen_field_type")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "列属性")
public class GenFieldType extends BaseEntity<GenFieldType> {

	/**
	 * 字段类型
	 */
	@Schema(description = "字段类型")
	private String columnType;

	/**
	 * 属性类型
	 */
	@Schema(description = "属性类型")
	private String attrType;

	/**
	 * 属性包名
	 */
	@Schema(description = "属性包名")
	private String packageName;

}
