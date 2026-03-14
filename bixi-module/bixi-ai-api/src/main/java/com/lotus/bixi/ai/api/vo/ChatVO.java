package com.lotus.bixi.ai.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 对话展示对象
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@Schema(description = "对话展示对象")
public class ChatVO implements Serializable {

    @Schema(description = "AI回答内容")
    private String content;

    @Schema(description = "会话ID")
    private String sessionId;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "token消耗")
    private Integer tokenCount;

    @Schema(description = "是否完成")
    private Boolean finished;

    private static final long serialVersionUID = 1L;
}
