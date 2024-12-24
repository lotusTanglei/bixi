

package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseRelationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 模板分组关联表
 *
 * @author tanglei
 * @date 2023-02-22 09:25:15
 */
@Data
@TableName("gen_template_group")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模板分组关联表")
public class GenTemplateGroup extends BaseRelationEntity<GenTemplateGroup> {

	/**
	 * 分组id
	 */
	@Schema(description = "分组id")
	private Long groupId;

	/**
	 * 模板id
	 */
	@Schema(description = "模板id")
	private Long templateId;

}
