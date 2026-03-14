package com.lotus.bixi.ai.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "引用来源展示对象")
public class SourceVO implements Serializable {

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "文档名称")
    private String documentName;

    @Schema(description = "引用内容")
    private String content;

    @Schema(description = "相似度分数")
    private Double score;

    private static final long serialVersionUID = 1L;
}
