
package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 字典项
 *
 * @author 唐磊
 * @date 2019/03/19
 */
@Data
@Schema(description = "字典项")
@EqualsAndHashCode(callSuper = true)
public class SysDictItem extends BaseEntity<SysDictItem> {

    private static final long serialVersionUID = 1L;

    /**
     * 所属字典类id
     */
    @Schema(description = "所属字典类id")
    private Long dictId;

    /**
     * 数据值
     */
    @Schema(description = "数据值")
    @JsonProperty(value = "value")
    private String value;

    /**
     * 标签名
     */
    @Schema(description = "标签名")
    private String label;

    /**
     * 类型
     */
    @Schema(description = "类型")
    private String dictType;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 排序（升序）
     */
    @Schema(description = "排序值，默认升序")
    private Integer sn;

}
