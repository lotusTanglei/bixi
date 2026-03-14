package com.lotus.bixi.ai.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 向量嵌入实体
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_embedding")
@Schema(description = "AI向量嵌入")
public class AiEmbedding extends BaseEntity<AiEmbedding> {

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "向量ID（向量数据库中的ID）")
    private String vectorId;

    @Schema(description = "嵌入模型")
    private String embeddingModel;

    @Schema(description = "向量维度")
    private Integer dimension;

    @Schema(description = "分块索引")
    private Integer chunkIndex;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
