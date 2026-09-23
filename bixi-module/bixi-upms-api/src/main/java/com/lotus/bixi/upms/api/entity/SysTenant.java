package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户表
 *
 * @author bixi
 * @date 2026-09-21
 */
@Data
@Schema(description = "租户")
@TableName("sys_tenant")
public class SysTenant extends Model<SysTenant> {

	@TableId(value = "id", type = IdType.AUTO)
	@Schema(description = "租户ID")
	private Long id;

	@Schema(description = "租户名称")
	private String name;

	@Schema(description = "租户编码")
	private String code;

	@Schema(description = "状态（0正常 1停用）")
	private String status;

	@Schema(description = "联系人")
	private String contact;

	@Schema(description = "联系电话")
	private String contactPhone;

	@Schema(description = "绑定域名")
	private String domain;

	@Schema(description = "过期时间")
	private LocalDateTime expireTime;

	@Schema(description = "最大用户数（-1不限）")
	private Integer maxUserCount;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "创建人")
	private String createBy;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "修改人")
	private String updateBy;

	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@Schema(description = "删除标记（0正常 1已删）")
	private String delFlag;

}
