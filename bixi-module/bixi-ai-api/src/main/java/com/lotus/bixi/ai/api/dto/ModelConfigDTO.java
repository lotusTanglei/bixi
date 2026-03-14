package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "模型配置传输对象")
public class ModelConfigDTO implements Serializable {

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "温度参数(0-2)")
    private Double temperature;

    @Schema(description = "最大token数")
    private Integer maxTokens;

    @Schema(description = "topP参数(0-1)")
    private Double topP;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    private static final long serialVersionUID = 1L;
}
