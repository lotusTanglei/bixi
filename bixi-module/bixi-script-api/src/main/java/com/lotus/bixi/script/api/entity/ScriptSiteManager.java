package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 现场负责人关联表
 *
 * @author bixi
 * @date 2024-05-20
 */
@Data
@TableName("script_site_manager")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "现场负责人关联表")
public class ScriptSiteManager extends BaseEntity<ScriptSiteManager> {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 现场ID
     */
    @Schema(description = "现场ID")
    private Long siteId;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID")
    private Long userId;

    /**
     * 是否主负责人（0否 1是）
     */
    @Schema(description = "是否主负责人（0否 1是）")
    private String isPrimary;

    /**
     * 管理角色（owner、backup、observer）
     */
    @Schema(description = "管理角色（owner、backup、observer）")
    private String roleType;

    /**
     * 状态（0有效 1无效）
     */
    @Schema(description = "状态（0有效 1无效）")
    private String status;

    /**
     * 租户id
     */
    @Schema(description = "租户id")
    private String tenantId;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

}
