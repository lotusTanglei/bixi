package com.lotus.bixi.common.security.service;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BixiUserDetailsFreshnessTest {

    @Test
    void oldDatabaseReadFinishingAfterDeletionDoesNotAuthorizeNextLogin() throws Exception {
        var oldReadComplete = new CountDownLatch(1);
        var letOldReadReturn = new CountDownLatch(1);
        var deleted = new AtomicBoolean();
        UserQueryService query = mock(UserQueryService.class);
        when(query.info(any(UserDTO.class))).thenAnswer(invocation -> {
            if (deleted.get()) return R.failed("user deleted");
            oldReadComplete.countDown();
            if (!letOldReadReturn.await(5, TimeUnit.SECONDS)) throw new AssertionError("old read was not released");
            return R.ok(userInfo("0"));
        });
        var users = new BixiUserDetailsServiceImpl(query);

        var inFlightLogin = CompletableFuture.supplyAsync(() -> users.loadUserByUsername("alice"));
        try {
            assertThat(oldReadComplete.await(5, TimeUnit.SECONDS)).isTrue();
            deleted.set(true);
        } finally {
            letOldReadReturn.countDown();
        }
        assertThat(inFlightLogin.get(5, TimeUnit.SECONDS).getUsername()).isEqualTo("alice");
        assertThatThrownBy(() -> users.loadUserByUsername("alice"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void lockingAccountIsVisibleOnNextPasswordLogin() {
        var locked = new AtomicBoolean();
        UserQueryService query = mock(UserQueryService.class);
        when(query.info(any(UserDTO.class))).thenAnswer(invocation -> R.ok(userInfo(locked.get() ? "1" : "0")));
        var users = new BixiUserDetailsServiceImpl(query);

        assertThat(users.loadUserByUsername("alice").isAccountNonLocked()).isTrue();
        locked.set(true);
        assertThat(users.loadUserByUsername("alice").isAccountNonLocked()).isFalse();
    }

    private static UserInfo userInfo(String lockFlag) {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setPassword("hash");
        user.setStatus("0");
        user.setLockFlag(lockFlag);
        UserInfo info = new UserInfo();
        info.setSysUser(user);
        return info;
    }
}
