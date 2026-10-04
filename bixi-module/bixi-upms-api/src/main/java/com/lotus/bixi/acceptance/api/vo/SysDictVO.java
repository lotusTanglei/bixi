package com.lotus.bixi.acceptance.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "字典表")
public class SysDictVO {
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
	@Schema(description = "备注")
	private String remark;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
	private List<SysDictItemVO> children;
}
