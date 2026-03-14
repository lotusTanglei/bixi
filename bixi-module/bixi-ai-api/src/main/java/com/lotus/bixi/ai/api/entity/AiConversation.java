package com.lotus.bixi.ai.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 对话记录实体
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
@Schema(description = "AI对话记录")
public class AiConversation extends BaseEntity<AiConversation> {

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "用户问题")
    private String question;

    @Schema(description = "AI回答")
    private String answer;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "token消耗")
    private Integer tokenCount;

    @Schema(description = "对话类型：chat/rag/stream")
    private String conversationType;

    @Schema(description = "用户ID")
    private Long userId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
