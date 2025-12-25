package com.lotus.bixi.upms.api.vo;

import com.lotus.bixi.upms.api.entity.SysUserNotice;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户通知VO
 *
 * @author bixi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户通知VO")
public class UserNoticeVO extends SysUserNotice {

    private static final long serialVersionUID = 1L;

    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "消息类型")
    private String type;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "发送人姓名")
    private String senderName;

    @Schema(description = "发送人头像")
    private String senderAvatar;

    @Schema(description = "接收人姓名")
    private String recipientName;
}
