package com.lotus.bixi.upms.api.vo;

import com.lotus.bixi.upms.api.entity.SysNotice;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息通知 VO
 *
 * @author bixi
 */
@Data
@Schema(description = "消息通知VO")
@EqualsAndHashCode(callSuper = true)
public class SysNoticeVO extends SysNotice {

    /**
     * 目标类型（0全体 1指定部门 2指定角色 3指定用户）
     */
    @Schema(description = "目标类型（0全体 1指定部门 2指定角色 3指定用户）")
    private String targetType;

    /**
     * 目标ID，逗号分隔
     */
    @Schema(description = "目标ID，逗号分隔")
    private String targetIds;
}
