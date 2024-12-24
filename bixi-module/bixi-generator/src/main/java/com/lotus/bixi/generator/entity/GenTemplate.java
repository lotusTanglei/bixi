

package com.lotus.bixi.generator.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 模板
 *
 * @author tanglei
 * @date 2023-02-21 17:15:44
 */
@Data
@TableName("gen_template")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "模板")
public class GenTemplate extends BaseEntity<GenTemplate> {
	/**
	 * 模板名称
	 */
	@Schema(description = "模板名称")
	private String templateName;

	/**
	 * 模板路径
	 */
	@Schema(description = "模板路径")
	private String generatorPath;

	/**
	 * 模板描述
	 */
	@Schema(description = "模板描述")
	private String templateDesc;

	/**
	 * 模板代码
	 */
	@Schema(description = "模板代码")
	private String templateCode;

}
