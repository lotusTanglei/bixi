package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 字典表
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
@Schema(description = "字典类型")
@EqualsAndHashCode(callSuper = true)
public class SysDict extends BaseEntity<SysDept> {

    private static final long serialVersionUID = 1L;
    /**
     * 类型
     */
    @Schema(description = "字典类型")
    private String type;

    /**
     * 类型
     */
    @Schema(description = "字典名称吧")
    private String name;

    /**
     * 描述
     */
    @Schema(description = "字典描述")
    private String description;

    /**
     * 是否是系统内置
     */
    @Schema(description = "是否系统内置")
    private String systemFlag;

    /**
     * 排序
     */
    @NotNull(message = "排序值不能为空")
    @Schema(description = "排序值")
    private Integer sn;

}
