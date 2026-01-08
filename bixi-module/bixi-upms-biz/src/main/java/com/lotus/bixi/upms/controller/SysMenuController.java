

package com.lotus.bixi.upms.controller;

import com.lotus.bixi.upms.api.entity.SysMenu;
import com.lotus.bixi.upms.service.SysMenuService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.util.SecurityUtils;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@RestController
@AllArgsConstructor
@RequestMapping("/menu")
@Tag(description = "menu", name = "菜单管理模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysMenuController {

    private final SysMenuService sysMenuService;

    /**
     * 返回当前用户的树形菜单集合
     *
     * @param type     类型
     * @param parentId 父节点ID
     * @return 当前用户的树形菜单
     */
    @GetMapping
    public R getUserMenu(String type, Long parentId) {
        // 获取符合条件的菜单
        Set<SysMenu> all = new HashSet<>();
        SecurityUtils.getRoles().forEach(roleId -> all.addAll(sysMenuService.findMenuByRoleId(roleId)));
        return R.ok(sysMenuService.filterMenu(all, type, parentId));
    }

    /**
     * 返回树形菜单集合
     *
     * @param parentId 父节点ID
     * @param name 菜单名称
     * @return 树形菜单
     */
    @GetMapping(value = "/tree")
    public R getTree(Long parentId, String name, String type) {
        return R.ok(sysMenuService.treeMenu(parentId, name, type));
    }

    /**
     * 返回角色的菜单集合
     *
     * @param roleId 角色ID
     * @return 属性集合
     */
    @GetMapping("/tree/{roleId}")
    public R getRoleTree(@PathVariable Long roleId) {
        return R
                .ok(sysMenuService.findMenuByRoleId(roleId).stream().map(SysMenu::getId).collect(Collectors.toList()));
    }

    /**
     * 通过ID查询菜单的详细信息
     *
     * @param id 菜单ID
     * @return 菜单详细信息
     */
    @GetMapping("/{id}")
    public R getById(@PathVariable Long id) {
        return R.ok(sysMenuService.getById(id));
    }

    /**
     * 新增菜单
     *
     * @param sysMenu 菜单信息
     * @return success/false
     */
    @SysLog("新增菜单")
    @PostMapping
    @HasPermission("sys_menu_add")
    public R save(@Valid @RequestBody SysMenu sysMenu) {
        sysMenuService.save(sysMenu);
        return R.ok(sysMenu);
    }

    /**
     * 删除菜单
     *
     * @param id 菜单ID
     * @return success/false
     */
    @SysLog("删除菜单")
    @DeleteMapping("/{id}")
    @HasPermission("sys_menu_del")
    public R removeById(@PathVariable Long id) {
        return sysMenuService.removeMenuById(id);
    }

    /**
     * 更新菜单
     *
     * @param sysMenu
     * @return
     */
    @SysLog("更新菜单")
    @PutMapping
    @HasPermission("sys_menu_edit")
    public R update(@Valid @RequestBody SysMenu sysMenu) {
        return R.ok(sysMenuService.updateMenuById(sysMenu));
    }

    /**
     * 清除菜单缓存
     */
    @SysLog("清除菜单缓存")
    @DeleteMapping("/cache")
    @HasPermission("sys_menu_del")
    public R clearMenuCache() {
        sysMenuService.clearMenuCache();
        return R.ok();
    }

}
