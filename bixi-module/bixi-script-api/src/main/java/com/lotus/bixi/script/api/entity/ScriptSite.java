package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 现场信息表
 *
 * @author bixi
 * @date 2025-01-01
 */
@Data
@TableName("script_site")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "现场信息表")
public class ScriptSite extends BaseEntity<ScriptSite> {

    private static final long serialVersionUID = 1L;

    /**
     * 现场ID
     */
    @TableId
    @Schema(description = "现场ID")
    private Long id;

    /**
     * 现场名称
     */
    @Schema(description = "现场名称")
    private String name;

    /**
     * 现场编码
     */
    @Schema(description = "现场编码")
    private String code;

    /**
     * 环境类型（prod、test、dev）
     */
    @Schema(description = "环境类型（prod、test、dev）")
    private String env;

    /**
     * 区域/节点
     */
    @Schema(description = "区域/节点")
    private String region;

    /**
     * 主要负责人ID
     */
    @Schema(description = "主要负责人ID")
    private Long ownerId;

    /**
     * 状态（0正常 1停用）
     */
    @Schema(description = "状态（0正常 1停用）")
    private String status;

    /**
     * 数据状态
     */
    @Schema(description = "数据状态")
    private String dataStatus;

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
