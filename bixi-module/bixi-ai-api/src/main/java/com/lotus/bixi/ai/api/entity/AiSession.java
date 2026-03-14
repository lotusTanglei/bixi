package com.lotus.bixi.ai.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_session")
@Schema(description = "AI会话")
public class AiSession extends BaseEntity<AiSession> {

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "会话状态：active/archived")
    private String status;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
