

package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 模板分组
 *
 * @author tanglei
 * @date 2023-02-21 20:01:53
 */
@Data
@TableName("gen_group")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模板分组")
public class GenGroup extends BaseEntity<GenGroup> {

	/**
	 * 分组名称
	 */
	@Schema(description = "分组名称")
	private String groupName;

	/**
	 * 分组描述
	 */
	@Schema(description = "分组描述")
	@TableField(fill = FieldFill.INSERT)
	private String groupDesc;

}
