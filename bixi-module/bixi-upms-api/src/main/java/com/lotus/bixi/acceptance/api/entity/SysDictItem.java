package com.lotus.bixi.acceptance.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@TableName("sys_dict_item")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "字典表明细")
public class SysDictItem extends BaseEntity<SysDictItem> {

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

}
