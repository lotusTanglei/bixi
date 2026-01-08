

package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 岗位信息表
 *
 * @author fxz
 * @date 2025-01-01
 */
@Data
@TableName("sys_post")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "岗位信息表")
public class SysPost extends BaseEntity<SysPost> {

    /**
     * 岗位编码
     */
    @NotBlank(message = "岗位编码不能为空")
    @Schema(description = "岗位编码")
    private String code;

    /**
     * 岗位名称
     */
    @NotBlank(message = "岗位名称不能为空")
    @Schema(description = "岗位名称")
    private String name;

    /**
     * 岗位排序
     */
    @NotNull(message = "排序值不能为空")
    @Schema(description = "岗位排序")
    private Integer sn;

}
