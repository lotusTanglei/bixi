package com.lotus.bixi.acceptance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "公共参数配置表查询条件")
public class SysPublicParamQueryDTO {

	@Schema(description = "名称")
	private String name;

	@Schema(description = "键")
	private String key;

	@Schema(description = "类型")
	private String type;

	@Schema(description = "系统标志")
	private String systemFlag;

}
