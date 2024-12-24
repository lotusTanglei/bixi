

package com.lotus.bixi.upms.api.entity;
import com.lotus.bixi.common.mybatis.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 菜单权限表
 * </p>
 *
 * @author 唐磊
 * @since 2017-11-08
 */
@Data
@Schema(description = "菜单")
@EqualsAndHashCode(callSuper = true)
public class SysMenu extends BaseEntity<SysMenu> {

    /**
     * 菜单名称
     */
    @NotBlank(message = "菜单名称不能为空")
    @Schema(description = "菜单名称")
    private String name;

    /**
     * 菜单名称
     */
    @Schema(description = "菜单名称")
    private String enName;

    /**
     * 菜单权限标识
     */
    @Schema(description = "菜单权限标识")
    private String permission;

    /**
     * 父菜单ID
     */
    @NotNull(message = "菜单父ID不能为空")
    @Schema(description = "菜单父id")
    private Long parentId;

    /**
     * 图标
     */
    @Schema(description = "菜单图标")
    private String icon;

    /**
     * 前端路由标识路径，默认和 comment 保持一致 过期
     */
    @Schema(description = "前端路由标识路径")
    private String path;

    /**
     * 菜单显示隐藏控制
     */
    @Schema(description = "菜单是否显示")
    private String visible;

    /**
     * 排序值
     */
    @Schema(description = "排序值")
    private Integer sn;

    /**
     * 菜单类型 （0菜单 1按钮）
     */
    @NotNull(message = "菜单类型不能为空")
    @Schema(description = "菜单类型,0:菜单 1:按钮")
    private String type;

    /**
     * 路由缓冲
     */
    @Schema(description = "路由缓冲")
    private String keepAlive;

    @Schema(description = "菜单是否内嵌")
    private String embedded;

}
