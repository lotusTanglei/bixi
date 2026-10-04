package com.lotus.bixi.acceptance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "导入公共参数配置表")
public class SysPublicParamImportDTO {

	@Size(max = 512, message = "名称长度不能超过512")
	@ExcelProperty("名称")
	@Schema(description = "名称")
	private String name;

	@Size(max = 512, message = "值长度不能超过512")
	@ExcelProperty("值")
	@Schema(description = "值")
	private String value;

	@Size(max = 512, message = "校验码长度不能超过512")
	@ExcelProperty("校验码")
	@Schema(description = "校验码")
	private String validateCode;

	@Size(max = 512, message = "类型，0未知，1系统，2业务长度不能超过512")
	@ExcelProperty("类型，0未知，1系统，2业务")
	@Schema(description = "类型，0未知，1系统，2业务")
	private String type;

	@Size(max = 512, message = "系统标识，0非系统，1系统长度不能超过512")
	@ExcelProperty("系统标识，0非系统，1系统")
	@Schema(description = "系统标识，0非系统，1系统")
	private String systemFlag;

	@ExcelProperty("排序")
	@Schema(description = "排序")
	private Integer sn;

}
