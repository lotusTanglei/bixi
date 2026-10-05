package com.lotus.bixi.acceptance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "字典表查询条件")
public class SysDictQueryDTO {

	@Schema(description = "字典类型")
	private String type;

	@Schema(description = "字典名称")
	private String name;

	@Schema(description = "系统标志")
	private String systemFlag;

}
