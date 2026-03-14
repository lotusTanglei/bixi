package com.lotus.bixi.ai.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "会话传输对象")
public class SessionDTO implements Serializable {

    @Schema(description = "会话ID")
    private Long id;

    @NotBlank(message = "会话标题不能为空")
    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "使用的模型")
    private String model;

    private static final long serialVersionUID = 1L;
}
