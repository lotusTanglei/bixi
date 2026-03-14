package com.lotus.bixi.ai.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 文档实体
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_document")
@Schema(description = "AI文档")
public class AiDocument extends BaseEntity<AiDocument> {

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "文档来源")
    private String source;

    @Schema(description = "文档类型")
    private String docType;

    @Schema(description = "向量状态：0-未向量化，1-已向量化")
    private Integer vectorStatus;

    @Schema(description = "用户ID")
    private Long userId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
