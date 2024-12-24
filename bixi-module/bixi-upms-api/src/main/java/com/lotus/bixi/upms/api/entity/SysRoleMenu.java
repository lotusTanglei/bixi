

package com.lotus.bixi.upms.api.entity;

import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.lotus.bixi.common.mybatis.base.BaseRelationEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 角色菜单表
 * </p>
 *
 * @author 唐磊
 * @since 2017-10-29
 */
@Data
@Schema(description = "角色菜单")
@EqualsAndHashCode(callSuper = true)
public class SysRoleMenu extends BaseRelationEntity<SysRoleMenu> {
    /**
     * 角色ID
     */
    @Schema(description = "角色id")
    private Long roleId;

    /**
     * 菜单ID
     */
    @Schema(description = "菜单id")
    private Long menuId;

}
