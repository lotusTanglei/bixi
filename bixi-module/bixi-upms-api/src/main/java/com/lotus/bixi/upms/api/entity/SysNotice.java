package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息通知表
 *
 * @author bixi
 * @date 2024-05-20
 */
@Data
@TableName("sys_notice")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "消息通知表")
public class SysNotice extends BaseEntity<SysNotice> {

    private static final long serialVersionUID = 1L;

    /**
     * 标题
     */
    @Schema(description = "标题")
    private String title;

    /**
     * 内容
     */
    @Schema(description = "内容")
    private String content;

    /**
     * 消息类型（0通知 1公告 2私信）
     */
    @Schema(description = "消息类型（0通知 1公告 2私信）")
    private String type;

    /**
     * 发送人ID
     */
    @Schema(description = "发送人ID")
    private Long senderId;

    /**
     * 优先级（0普通 1重要 2紧急）
     */
    @Schema(description = "优先级（0普通 1重要 2紧急）")
    private String priority;
}
