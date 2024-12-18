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

import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件管理
 *
 * @author 唐磊
 * @date 2019-06-18 17:18:42
 */
@Data
@Schema(description = "文件")
@EqualsAndHashCode(callSuper = true)
public class SysFile extends BaseEntity<SysFile> {
    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String name;

    /**
     * 原文件名
     */
    @Schema(description = "原始文件名")
    private String original;

    /**
     * 容器名称
     */
    @Schema(description = "存储桶名称")
    private String bucket;

    /**
     * 文件类型
     */
    @Schema(description = "文件类型")
    private String type;

    /**
     * 文件大小
     */
    @Schema(description = "文件大小")
    private Long size;

    /**
     * 来源、标明是哪个表或者哪个模块、业务的文件
     */
    @Schema(description = "来源")
    private String source;

    /**
     * 业务、标明是来源的哪个业务
     */
    @Schema(description = "业务")
    private String business;


    /**
     * 来源唯一标识，通常是哪张表的附件填哪张表的主键id
     */
    @Schema(description = "来源ID")
    private Long sourceId;

}
