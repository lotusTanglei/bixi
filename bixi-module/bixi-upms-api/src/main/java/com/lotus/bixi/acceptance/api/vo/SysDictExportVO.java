package com.lotus.bixi.acceptance.api.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "导出字典表")
public class SysDictExportVO {

	@ExcelProperty("字典类型")
	@Schema(description = "字典类型")
	private String type;

	@ExcelProperty("字典名称")
	@Schema(description = "字典名称")
	private String name;

	@ExcelProperty("字典描述")
	@Schema(description = "字典描述")
	private String description;

	@ExcelProperty("排序号")
	@Schema(description = "排序号")
	private Integer sn;

	@ExcelProperty("系统标志")
	@Schema(description = "系统标志")
	private String systemFlag;

}
