

package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseRelationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * <p>
 * 用户角色表
 * </p>
 *
 * @author 唐磊
 * @since 2017-10-29
 */
@Data
@Schema(description = "用户角色")
@EqualsAndHashCode(callSuper = true)
public class SysUserRole extends BaseRelationEntity<SysUserRole> {
    /**
     * 用户ID
     */
    @Schema(description = "用户id")
    private Long userId;

    /**
     * 角色ID
     */
    @Schema(description = "角色id")
    private Long roleId;
}
