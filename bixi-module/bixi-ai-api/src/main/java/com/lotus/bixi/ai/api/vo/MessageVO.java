package com.lotus.bixi.ai.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "消息展示对象")
public class MessageVO implements Serializable {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "会话ID")
    private Long sessionId;

    @Schema(description = "角色：user/assistant")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "token数量")
    private Integer tokenCount;

    @Schema(description = "引用来源")
    private List<SourceVO> sources;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
