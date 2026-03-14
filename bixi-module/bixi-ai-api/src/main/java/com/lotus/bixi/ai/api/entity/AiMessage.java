package com.lotus.bixi.ai.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
@Schema(description = "AI消息")
public class AiMessage extends BaseEntity<AiMessage> {

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "角色：user/assistant")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "token数量")
    private Integer tokenCount;

    @Schema(description = "引用来源JSON")
    private String sources;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
