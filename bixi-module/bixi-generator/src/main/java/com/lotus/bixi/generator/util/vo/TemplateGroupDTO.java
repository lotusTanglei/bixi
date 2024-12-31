package com.lotus.bixi.generator.util.vo;

import com.lotus.bixi.generator.entity.GenGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@Schema(description = "模板传输对象")
@EqualsAndHashCode(callSuper = true)
public class TemplateGroupDTO extends GenGroup {

	/**
	 * 模板id集合
	 */
	@Schema(description = "模板id集合")
	private List<Long> templateId;

}
