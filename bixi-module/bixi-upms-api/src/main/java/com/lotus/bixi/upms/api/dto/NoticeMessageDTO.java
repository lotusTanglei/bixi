package com.lotus.bixi.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 通知消息传输对象（用于 MQ 传输）
 *
 * @author bixi
 */
@Data
@Schema(description = "通知消息传输对象")
public class NoticeMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容（支持HTML）")
    private String content;

    @Schema(description = "接收人ID列表")
    private List<Long> receiverIds;

    @Schema(description = "发送人ID（系统发送可为空或填-1）")
    private Long senderId;

    @Schema(description = "消息类型（0通知 1公告 2私信）")
    private String type;

    @Schema(description = "业务ID（可选，用于跳转）")
    private String bizId;

    @Schema(description = "业务类型（可选）")
    private String bizType;

    @Schema(description = "通知ID（已有通知ID）")
    private Long noticeId;

    @Schema(description = "通告对象类型（0全体成员 1部门 2角色 3指定用户）")
    private String targetType;

    @Schema(description = "通告对象ID（逗号分隔）")
    private String targetIds;

}
