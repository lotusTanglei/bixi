package com.lotus.bixi.ai.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Durable, tenant-scoped model defaults used by the AI chat services.
 *
 * Provider credentials deliberately do not belong in this entity. They are
 * supplied through deployment secrets and are never exposed by the config API.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
@Schema(description = "AI模型配置")
public class AiModelConfig extends BaseEntity<AiModelConfig> {

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
