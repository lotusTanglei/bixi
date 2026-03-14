package com.lotus.bixi.ai.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档 VO
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@Schema(description = "文档信息")
public class DocumentVO implements Serializable {

    @Schema(description = "文档ID")
    private Long id;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "文档来源")
    private String source;

    @Schema(description = "文档类型")
    private String docType;

    @Schema(description = "向量状态")
    private Integer vectorStatus;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "相似度分数")
    private Double score;

    private static final long serialVersionUID = 1L;
}
