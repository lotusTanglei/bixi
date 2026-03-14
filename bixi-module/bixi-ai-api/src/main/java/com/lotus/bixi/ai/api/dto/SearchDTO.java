package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 相似度搜索传输对象
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@Schema(description = "相似度搜索传输对象")
public class SearchDTO implements Serializable {

    @NotBlank(message = "查询内容不能为空")
    @Schema(description = "查询内容")
    private String query;

    @Min(value = 1, message = "返回数量最小为1")
    @Max(value = 100, message = "返回数量最大为100")
    @Schema(description = "返回结果数量")
    private Integer topK = 5;

    @Schema(description = "相似度阈值")
    private Double threshold;

    @Schema(description = "文档ID列表")
    private List<Long> documentIds;

    private static final long serialVersionUID = 1L;
}
