

package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.entity.SysRoleMenu;
import com.lotus.bixi.upms.api.entity.SysRole;
import com.lotus.bixi.upms.api.entity.SysMenu;
import com.lotus.bixi.upms.mapper.SysRoleMenuMapper;
import com.lotus.bixi.upms.mapper.SysRoleMapper;
import com.lotus.bixi.upms.mapper.SysMenuMapper;
import com.lotus.bixi.upms.service.SysRoleMenuService;
import com.lotus.bixi.common.core.constant.CacheConstants;
import lombok.AllArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色菜单表 服务实现类
 * </p>
 *
 * @author 唐磊
 * @since 2017-10-29
 */
@Service
@AllArgsConstructor
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements SysRoleMenuService {

    private final CacheManager cacheManager;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    /**
     * @param roleId  角色
     * @param menuIds 菜单ID拼成的字符串，每个id之间根据逗号分隔
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveRoleMenus(Long roleId, String menuIds) {
        if (roleId == null || roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getId, roleId)) != 1) {
            throw new IllegalArgumentException("角色不存在或不属于当前租户");
        }
        List<Long> requestedMenus = StrUtil.isBlank(menuIds) ? List.of() : Arrays.stream(menuIds.split(StrUtil.COMMA))
                .map(Long::valueOf).toList();
        Set<Long> distinctMenus = Set.copyOf(requestedMenus);
        if (!distinctMenus.isEmpty() && menuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .in(SysMenu::getId, distinctMenus)) != distinctMenus.size()) {
            throw new IllegalArgumentException("菜单不存在或不属于当前租户");
        }
        this.remove(Wrappers.<SysRoleMenu>query().lambda().eq(SysRoleMenu::getRoleId, roleId));

        if (!requestedMenus.isEmpty()) {
            List<SysRoleMenu> roleMenuList = requestedMenus.stream().map(menuId -> {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                return roleMenu;
            }).collect(Collectors.toList());
            this.saveBatch(roleMenuList);
        }

        // 包括撤回全部菜单；成功提交后再失效，跟随调用方的外层事务。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 菜单缓存含 roleId:workflow.enabled 等组合键，需要清理全部变体。
                cacheManager.getCache(CacheConstants.MENU_DETAILS).clear();
                cacheManager.getCache(CacheConstants.ROLE_DETAILS).clear();
                // 用户权限依赖菜单缓存，最后失效以免在清理间隙从旧菜单重建用户授权。
                cacheManager.getCache(CacheConstants.USER_DETAILS).clear();
            }
        });
        return Boolean.TRUE;
    }

}
