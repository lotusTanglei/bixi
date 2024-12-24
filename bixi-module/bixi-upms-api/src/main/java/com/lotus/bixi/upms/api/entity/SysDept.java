

package com.lotus.bixi.upms.api.entity;

import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 部门管理
 * </p>
 *
 * @author 唐磊
 * @since 2018-01-22
 */
@Data
@Schema(description = "部门")
@EqualsAndHashCode(callSuper = true)
public class SysDept extends BaseEntity<SysDept> {

    private static final long serialVersionUID = 1L;

    /**
     * 部门名称
     */
    @NotBlank(message = "部门名称不能为空")
    @Schema(description = "部门名称")
    private String name;

    /**
     * 部门名称
     */
    @NotBlank(message = "部门编码不能为空")
    @Schema(description = "部门编码")
    private String code;
    /**
     * 排序
     */
    @NotNull(message = "排序值不能为空")
    @Schema(description = "排序值")
    private Integer sn;

    /**
     * 父级部门id
     */
    @Schema(description = "父级部门id")
    private Long parentId;

}
