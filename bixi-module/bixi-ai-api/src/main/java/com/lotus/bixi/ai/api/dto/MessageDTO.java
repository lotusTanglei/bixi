package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "消息传输对象")
public class MessageDTO implements Serializable {

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID")
    private Long sessionId;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "文档ID列表（RAG模式）")
    private List<Long> documentIds;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "温度参数")
    private Double temperature;

    @Schema(description = "最大token数")
    private Integer maxTokens;

    @Schema(description = "topP参数")
    private Double topP;

    private static final long serialVersionUID = 1L;
}
