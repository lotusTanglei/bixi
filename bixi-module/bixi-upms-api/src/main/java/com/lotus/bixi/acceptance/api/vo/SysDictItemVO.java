package com.lotus.bixi.acceptance.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Schema(description = "字典表明细")
public class SysDictItemVO {
	private Long id;
	@Schema(description = "字典ID")
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
	@Schema(description = "备注")
	private String remark;
}
