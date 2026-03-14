package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 向量嵌入传输对象
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@Schema(description = "向量嵌入传输对象")
public class EmbeddingDTO implements Serializable {

    @NotBlank(message = "文本内容不能为空")
    @Schema(description = "待嵌入的文本")
    private String text;

    @Schema(description = "嵌入模型")
    private String model;

    private static final long serialVersionUID = 1L;
}
