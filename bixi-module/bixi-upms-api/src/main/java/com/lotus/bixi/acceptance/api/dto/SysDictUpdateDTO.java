package com.lotus.bixi.acceptance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "修改字典表")
public class SysDictUpdateDTO {

	@NotNull(message = "ID不能为空")
	private Long id;

	@Schema(description = "字典类型")
	private String type;

	@Schema(description = "字典名称")
	private String name;

	@Schema(description = "字典描述")
	private String description;

	@Schema(description = "排序号")
	private Integer sn;

	@Schema(description = "系统标志")
	private String systemFlag;

	@Schema(description = "状态（0正常 1停用）")
	private String status;

	@Schema(description = "数据状态（用来标识数据状态，可用于割接，特殊数据处理）")
	private String dataStatus;

	@Schema(description = "备注")
	private String remark;

	@Valid
	@NotNull(message = "明细集合不能为空")
	private List<SysDictItemDTO> children;
}
