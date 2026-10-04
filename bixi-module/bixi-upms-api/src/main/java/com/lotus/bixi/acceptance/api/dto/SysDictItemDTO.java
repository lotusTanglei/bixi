package com.lotus.bixi.acceptance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "字典表明细")
public class SysDictItemDTO {

	@Schema(description = "明细ID")
	private Long id;

	@Schema(description = "主表关系键")
	private Long dictId;

	@Schema(description = "字典项值")
	private String value;

	@Schema(description = "字典项标签")
	private String label;

	@Schema(description = "字典类型")
	private String dictType;

	@Schema(description = "字典项描述")
	private String description;

	@Schema(description = "排序")
	private Integer sn;

	@Schema(description = "状态（0正常 1停用）")
	private String status;

	@Schema(description = "数据状态（用来标识数据状态，可用于割接，特殊数据处理）")
	private String dataStatus;

	@Schema(description = "备注")
	private String remark;

}
