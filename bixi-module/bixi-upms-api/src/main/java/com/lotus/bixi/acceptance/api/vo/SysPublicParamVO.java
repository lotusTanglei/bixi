package com.lotus.bixi.acceptance.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

@Data
@Schema(description = "公共参数配置表")
public class SysPublicParamVO {
	private Long id;
	@Schema(description = "名称")
	private String name;
	@Schema(description = "键")
	private String key;
	@Schema(description = "值")
	private String value;
	@Schema(description = "校验码")
	private String validateCode;
	@Schema(description = "类型，0未知，1系统，2业务")
	private String type;
	@Schema(description = "系统标识，0非系统，1系统")
	private String systemFlag;
	@Schema(description = "排序")
	private Integer sn;
	@Schema(description = "状态（0正常 1停用）")
	private String status;
	@Schema(description = "备注")
	private String remark;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}
