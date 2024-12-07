/*
 *    Copyright (c) 2018-2025, lengleng All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the pig4cloud.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: lengleng (wangiegie@gmail.com)
 */

package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 公共参数配置
 *
 * @author 唐磊
 * @date 2019-04-29
 */
@Data
@Schema(description = "公共参数")
@EqualsAndHashCode(callSuper = true)
public class SysPublicParam extends BaseEntity<SysPublicParam> {

    /**
     * 公共参数名称
     */
    @Schema(description = "公共参数名称", required = true, example = "公共参数名称")
    private String name;

    /**
     * 公共参数地址值,英文大写+下划线
     */
    @Schema(description = "键[英文大写+下划线]", required = true, example = "PIGX_PUBLIC_KEY")
    @TableField(value = "`key`")
    private String key;

    /**
     * 值
     */
    @Schema(description = "值", required = true, example = "999")
    private String value;

    /**
     * 公共参数编码
     */
    @Schema(description = "编码", example = "^(BIXI|LOTUS)$")
    private String validateCode;

    /**
     * 是否是系统内置
     */
    @Schema(description = "是否是系统内置")
    private String systemFlag;

    /**
     * 配置类型：0-默认；1-检索；2-原文；3-报表；4-安全；5-文档；6-消息；9-其他
     */
    @Schema(description = "类型[1-检索；2-原文...]", example = "1")
    private String type;

    /**
     * 岗位排序
     */
    @NotNull(message = "排序值不能为空")
    @Schema(description = "参数排序")
    private Integer sn;


}
