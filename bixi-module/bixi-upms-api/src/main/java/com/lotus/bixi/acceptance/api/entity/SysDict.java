package com.lotus.bixi.acceptance.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典表")
public class SysDict extends BaseEntity<SysDict> {

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

}
