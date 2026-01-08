

package com.lotus.bixi.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Data
@Schema(description = "前端角色展示对象")
public class RoleVO {

    /**
     * 角色id
     */
    private Long id;

    /**
     * 菜单列表
     */
    private String menuIds;

}
