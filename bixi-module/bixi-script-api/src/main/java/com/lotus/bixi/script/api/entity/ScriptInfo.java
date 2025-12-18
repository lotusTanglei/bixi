package com.lotus.bixi.script.api.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 脚本主信息表
 *
 * @author bixi
 * @date 2024-05-20
 */
@Data
@TableName("script_info")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "脚本主信息表")
public class ScriptInfo extends BaseEntity<ScriptInfo> {

    private static final long serialVersionUID = 1L;

    /**
     * 脚本ID
     */
    @TableId
    @Schema(description = "脚本ID")
    private Long id;

    /**
     * 脚本名称
     */
    @Schema(description = "脚本名称")
    private String name;

    /**
     * 脚本编码
     */
    @Schema(description = "脚本编码")
    private String code;

    /**
     * 版本号
     */
    @Schema(description = "版本号")
    private String version;

    /**
     * 类型（0DDL 1DML 2其他）
     */
    @Schema(description = "类型（0DDL 1DML 2其他）")
    private String type;

    /**
     * 风险级别（0低 1中 2高）
     */
    @Schema(description = "风险级别（0低 1中 2高）")
    private String riskLevel;

    /**
     * 存储路径
     */
    @Schema(description = "存储路径")
    private String storagePath;

    /**
     * 校验摘要
     */
    @Schema(description = "校验摘要")
    private String checksum;

    /**
     * 状态（0草稿 1已发布 2已废弃）
     */
    @Schema(description = "状态（0草稿 1已发布 2已废弃）")
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
