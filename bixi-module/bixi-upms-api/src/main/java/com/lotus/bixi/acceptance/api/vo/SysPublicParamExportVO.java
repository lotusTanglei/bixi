package com.lotus.bixi.acceptance.api.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "导出公共参数配置表")
public class SysPublicParamExportVO {

	@ExcelProperty("名称")
	@Schema(description = "名称")
	private String name;

	@ExcelProperty("键")
	@Schema(description = "键")
	private String key;

	@ExcelProperty("值")
	@Schema(description = "值")
	private String value;

	@ExcelProperty("校验码")
	@Schema(description = "校验码")
	private String validateCode;

	@ExcelProperty("类型，0未知，1系统，2业务")
	@Schema(description = "类型，0未知，1系统，2业务")
	private String type;

	@ExcelProperty("系统标识，0非系统，1系统")
	@Schema(description = "系统标识，0非系统，1系统")
	private String systemFlag;

	@ExcelProperty("排序")
	@Schema(description = "排序")
	private Integer sn;

}
