package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "敏感词")
@TableName("sys_sensitive_word")
public class SysSensitiveWord extends Model<SysSensitiveWord> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	@NotBlank
	private String word;
	private String category;
	private String status;
	private Long tenantId;
	private java.time.LocalDateTime createTime;
	private java.time.LocalDateTime updateTime;
	@TableLogic
	private String delFlag;

}
