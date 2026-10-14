package com.lotus.bixi.common.security.service;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.service.UserQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BixiAppUserDetailsServiceImplTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void reloadsMaskedMobilePrincipalByUsername() {
        UserQueryService query = mock(UserQueryService.class);
        when(query.info(any(UserDTO.class))).thenAnswer(invocation -> {
            UserDTO requested = invocation.getArgument(0);
            if (!"13800000000".equals(requested.getPhone()) && !"alice".equals(requested.getUsername())) {
                return R.failed("User not found");
            }
            return R.ok(userInfo(41L, "alice", "138****0000"));
        });
        var usernameUsers = new BixiUserDetailsServiceImpl(query);
        var mobileUsers = new BixiAppUserDetailsServiceImpl(query, usernameUsers);

        TenantContextHolder.set(1L);
        BixiUser original = (BixiUser) mobileUsers.loadUserByUsername("13800000000");
        TenantContextHolder.clear();
        assertThat(original.getPhone()).isEqualTo("138****0000");

        BixiUser current = (BixiUser) mobileUsers.loadUserByUser(original);
        assertThat(current.getId()).isEqualTo(41L);
        assertThat(current.getUsername()).isEqualTo("alice");
        assertThat(TenantContextHolder.get()).isNull();

        mobileUsers.loadUserByUser(original);
        verify(query, times(3)).info(any(UserDTO.class));
    }

    @Test
    void mobileLoginDoesNotReuseAnotherUsersUsernameCacheEntry() {
        UserQueryService query = mock(UserQueryService.class);
        when(query.info(any(UserDTO.class))).thenAnswer(invocation -> {
            UserDTO requested = invocation.getArgument(0);
            if ("13800000000".equals(requested.getUsername())) {
                return R.ok(userInfo(41L, "13800000000", "13900000000"));
            }
            if ("13800000000".equals(requested.getPhone())) {
                return R.ok(userInfo(42L, "bob", "138****0000"));
            }
            return R.failed("User not found");
        });
        var usernameUsers = new BixiUserDetailsServiceImpl(query);
        var mobileUsers = new BixiAppUserDetailsServiceImpl(query, usernameUsers);
        TenantContextHolder.set(1L);

        BixiUser usernameOwner = (BixiUser) usernameUsers.loadUserByUsername("13800000000");
        BixiUser phoneOwner = (BixiUser) mobileUsers.loadUserByUsername("13800000000");

        assertThat(usernameOwner.getId()).isEqualTo(41L);
        assertThat(phoneOwner.getId()).isEqualTo(42L);
        assertThat(phoneOwner.getUsername()).isEqualTo("bob");
        verify(query, times(2)).info(any(UserDTO.class));
    }

    private static UserInfo userInfo(Long id, String username, String phone) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(1L);
        user.setUsername(username);
        user.setPhone(phone);
        user.setStatus("0");
        user.setLockFlag("0");
        UserInfo info = new UserInfo();
        info.setSysUser(user);
        info.setEncodedPassword("encoded-password");
        return info;
    }
}
