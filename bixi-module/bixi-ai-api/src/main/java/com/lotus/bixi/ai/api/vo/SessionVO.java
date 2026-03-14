package com.lotus.bixi.ai.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "会话展示对象")
public class SessionVO implements Serializable {

    @Schema(description = "会话ID")
    private Long id;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "使用的模型")
    private String model;

    @Schema(description = "会话状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;
}
