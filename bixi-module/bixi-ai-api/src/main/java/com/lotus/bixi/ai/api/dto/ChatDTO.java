package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话传输对象
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@Schema(description = "对话传输对象")
public class ChatDTO implements Serializable {

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "用户消息")
    private String message;

    @Schema(description = "会话ID，用于保持上下文")
    private String sessionId;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "温度参数")
    private Double temperature;

    @Schema(description = "最大token数")
    private Integer maxTokens;

    private static final long serialVersionUID = 1L;
}
