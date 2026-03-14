package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 文档传输对象
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@Schema(description = "文档传输对象")
public class DocumentDTO implements Serializable {

    @NotBlank(message = "文档标题不能为空")
    @Schema(description = "文档标题")
    private String title;

    @NotBlank(message = "文档内容不能为空")
    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "文档来源")
    private String source;

    @Schema(description = "文档类型")
    private String docType;

    private static final long serialVersionUID = 1L;
}
