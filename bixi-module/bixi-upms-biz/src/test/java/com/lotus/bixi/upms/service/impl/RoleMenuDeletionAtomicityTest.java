package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.upms.mapper.SysMenuMapper;
import com.lotus.bixi.upms.mapper.SysRoleMapper;
import com.lotus.bixi.upms.mapper.SysRoleMenuMapper;
import com.lotus.bixi.upms.service.SysRoleMenuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleMenuDeletionAtomicityTest {

    @Mock SysRoleMapper roleMapper;
    @Mock SysRoleMenuService roleMenus;
    @Mock SysMenuMapper menuMapper;
    @Mock SysRoleMenuMapper menuRoles;
    @Mock Environment environment;

    @Test
    void partialRoleDeleteDoesNotRemoveMenuRelations() {
        SysRoleServiceImpl service = new SysRoleServiceImpl(roleMenus, new ConcurrentMapCacheManager());
        ReflectionTestUtils.setField(service, "baseMapper", roleMapper);
        when(roleMapper.selectCount(any())).thenReturn(2L);
        when(roleMapper.deleteByIds(anyCollection())).thenReturn(1);

        assertThatThrownBy(() -> service.removeRoleByIds(new Long[]{7L, 8L}))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(roleMenus);
    }

    @Test
    void unsuccessfulMenuDeleteDoesNotRemoveRoleRelations() {
        SysMenuServiceImpl service = new SysMenuServiceImpl(menuRoles, environment,
                new ConcurrentMapCacheManager());
        ReflectionTestUtils.setField(service, "baseMapper", menuMapper);
        when(menuMapper.selectCount(any())).thenReturn(1L);
        when(menuMapper.selectList(any())).thenReturn(List.of());
        when(menuMapper.deleteById(10L)).thenReturn(0);

        assertThat(service.removeMenuById(10L).getCode()).isNotEqualTo(0);
        verifyNoInteractions(menuRoles);
    }
}
