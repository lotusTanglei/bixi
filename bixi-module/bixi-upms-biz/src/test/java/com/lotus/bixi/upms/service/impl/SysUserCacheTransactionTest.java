package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.mapper.SysUserMapper;
import com.lotus.bixi.upms.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class SysUserCacheTransactionTest {

    private static final String USERNAME = "alice";

    private ConcurrentMapCacheManager caches;
    private TransactionTemplate transactions;
    private SysUserServiceImpl service;
    private String cacheKey;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(7L);
        cacheKey = CacheConstants.currentTenantKey(CacheConstants.USER_DETAILS) + USERNAME;
        caches = new ConcurrentMapCacheManager(CacheConstants.USER_DETAILS);
        var source = new DriverManagerDataSource("jdbc:h2:mem:user-cache-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1", "sa", "");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));

        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername(USERNAME);
        SysUserMapper users = mock(SysUserMapper.class);
        when(users.selectByIds(anyCollection())).thenReturn(List.of(user));
        when(users.deleteById(42L)).thenReturn(1);
        service = spy(new SysUserServiceImpl(null, null, null, null, mock(SysUserRoleMapper.class),
                null, null, caches, null));
        ReflectionTestUtils.setField(service, "baseMapper", users);
        doReturn(user).when(service).getById(42L);
        doReturn(true).when(service).updateById(any(SysUser.class));
        caches.getCache(CacheConstants.USER_DETAILS).put(cacheKey, "cached-before-write");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void deletionEvictsOnlyAfterCommitIncludingConcurrentRefills() {
        transactions.executeWithoutResult(status -> {
            assertThat(service.deleteUserByIds(new Long[]{42L})).isTrue();
            assertThat(cachedValue()).isEqualTo("cached-before-write");
            CompletableFuture.runAsync(() -> caches.getCache(CacheConstants.USER_DETAILS)
                    .put(cacheKey, "stale-concurrent-login")).join();
        });

        assertThat(cachedValue()).isNull();
    }

    @Test
    void rolledBackDeletionDoesNotEvictUserDetails() {
        transactions.executeWithoutResult(status -> {
            assertThat(service.deleteUserByIds(new Long[]{42L})).isTrue();
            assertThat(cachedValue()).isEqualTo("cached-before-write");
            status.setRollbackOnly();
        });

        assertThat(cachedValue()).isEqualTo("cached-before-write");
    }

    @Test
    void userUpdateInsideOuterTransactionEvictsOnlyAfterCommit() {
        UserDTO updated = new UserDTO();
        updated.setId(42L);
        updated.setUsername(USERNAME);

        transactions.executeWithoutResult(status -> {
            assertThat(service.updateUser(updated)).isTrue();
            assertThat(cachedValue()).isEqualTo("cached-before-write");
        });

        assertThat(cachedValue()).isNull();
    }

    private String cachedValue() {
        return caches.getCache(CacheConstants.USER_DETAILS).get(cacheKey, String.class);
    }
}
