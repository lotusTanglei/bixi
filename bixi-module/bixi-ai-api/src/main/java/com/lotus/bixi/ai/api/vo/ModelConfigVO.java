package com.lotus.bixi.ai.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Schema(description = "模型配置展示对象")
public class ModelConfigVO implements Serializable {

    @Schema(description = "当前模型")
    private String currentModel;

    @Schema(description = "温度参数")
    private Double temperature;

    @Schema(description = "最大token数")
    private Integer maxTokens;

    @Schema(description = "topP参数")
    private Double topP;

    @Schema(description = "系统提示词")
    private String systemPrompt;

    @Schema(description = "可用模型列表")
    private List<ModelInfo> availableModels;

    private static final long serialVersionUID = 1L;

    @Data
    public static class ModelInfo implements Serializable {
        @Schema(description = "模型ID")
        private String id;

        @Schema(description = "模型名称")
        private String name;

        @Schema(description = "模型描述")
        private String description;

        private static final long serialVersionUID = 1L;
    }
}
