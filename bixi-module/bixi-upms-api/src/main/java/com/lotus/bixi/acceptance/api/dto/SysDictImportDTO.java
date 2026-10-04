package com.lotus.bixi.acceptance.api.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "导入字典表")
public class SysDictImportDTO {

	@Size(max = 512, message = "字典类型长度不能超过512")
	@ExcelProperty("字典类型")
	@Schema(description = "字典类型")
	private String type;

	@Size(max = 512, message = "字典名称长度不能超过512")
	@ExcelProperty("字典名称")
	@Schema(description = "字典名称")
	private String name;

	@Size(max = 512, message = "字典描述长度不能超过512")
	@ExcelProperty("字典描述")
	@Schema(description = "字典描述")
	private String description;

	@ExcelProperty("排序号")
	@Schema(description = "排序号")
	private Integer sn;

	@Size(max = 512, message = "系统标志长度不能超过512")
	@ExcelProperty("系统标志")
	@Schema(description = "系统标志")
	private String systemFlag;

}
