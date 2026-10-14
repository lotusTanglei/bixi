package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.entity.SysTenant;
import com.lotus.bixi.upms.mapper.SysUserMapper;
import com.lotus.bixi.upms.mapper.SysUserRoleMapper;
import com.lotus.bixi.upms.mapper.SysTenantMapper;
import com.lotus.bixi.upms.service.SysPostService;
import com.lotus.bixi.upms.service.SysRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class SysUserRelationOwnershipTest {

    private SysUserMapper users;
    private SysUserRoleMapper userRoles;
    private SysRoleService roles;
    private SysPostService posts;
    private SysTenantMapper tenants;
    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(7L);
        users = mock(SysUserMapper.class);
        userRoles = mock(SysUserRoleMapper.class);
        roles = mock(SysRoleService.class);
        posts = mock(SysPostService.class);
        tenants = mock(SysTenantMapper.class);
        service = spy(new SysUserServiceImpl(null, roles, posts, null, userRoles, null, tenants,
                new ConcurrentMapCacheManager("user_details"), null));
        ReflectionTestUtils.setField(service, "baseMapper", users);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void deletingAnInvisibleUserDoesNotRemoveTheirRoles() {
        when(users.selectByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteUserByIds(new Long[]{42L}))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRoles);
    }

    @Test
    void rejectedUserDeleteDoesNotRemoveTheirRoles() {
        when(users.selectByIds(any())).thenReturn(List.of(currentUser()));

        assertThatThrownBy(() -> service.deleteUserByIds(new Long[]{42L}))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users).deleteById(42L);
        verifyNoInteractions(userRoles);
    }

    @Test
    void updatingAnInvisibleUserDoesNotRemoveTheirRoles() {
        UserDTO update = update(42L);
        doReturn(true).when(service).updateById(any(SysUser.class));

        assertThatThrownBy(() -> service.updateUser(update))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRoles);
    }

    @Test
    void rejectedUserUpdateDoesNotRemoveTheirRoles() {
        UserDTO update = update(42L);
        SysUser current = new SysUser();
        current.setId(42L);
        current.setUsername("alice");
        doReturn(current).when(service).getById(42L);
        doReturn(false).when(service).updateById(any(SysUser.class));

        assertThatThrownBy(() -> service.updateUser(update))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRoles);
    }

    @Test
    void assigningAnInvisibleRoleDoesNotUpdateTheUser() {
        UserDTO update = update(42L);
        update.setRole(List.of(99L));
        doReturn(currentUser()).when(service).getById(42L);

        assertThatThrownBy(() -> service.updateUser(update))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).updateById(any(SysUser.class));
        verifyNoInteractions(userRoles);
    }

    @Test
    void assigningAnInvisiblePostDoesNotUpdateTheUser() {
        UserDTO update = update(42L);
        update.setPost(List.of(99L));
        doReturn(currentUser()).when(service).getById(42L);

        assertThatThrownBy(() -> service.updateUser(update))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).updateById(any(SysUser.class));
        verifyNoInteractions(userRoles);
    }

    @Test
    void creatingAUserWithAnInvisibleRoleDoesNotInsertTheUser() {
        UserDTO newUser = update(null);
        newUser.setPassword("StrongPass123!");
        newUser.setRole(List.of(99L));
        SysTenant tenant = new SysTenant();
        tenant.setId(7L);
        tenant.setStatus("0");
        when(tenants.selectById(7L)).thenReturn(tenant);

        assertThatThrownBy(() -> service.saveUser(newUser))
                .isInstanceOf(IllegalArgumentException.class);
        verify(users, never()).insert(any(SysUser.class));
        verifyNoInteractions(userRoles);
    }

    private static SysUser currentUser() {
        SysUser current = new SysUser();
        current.setId(42L);
        current.setUsername("alice");
        return current;
    }

    private static UserDTO update(Long id) {
        UserDTO user = new UserDTO();
        user.setId(id);
        user.setUsername("alice");
        user.setRole(List.of());
        return user;
    }
}
