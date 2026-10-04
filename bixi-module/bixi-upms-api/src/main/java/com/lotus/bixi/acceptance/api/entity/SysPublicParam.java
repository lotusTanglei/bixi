package com.lotus.bixi.acceptance.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@TableName("sys_public_param")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "公共参数配置表")
public class SysPublicParam extends BaseEntity<SysPublicParam> {

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

}
