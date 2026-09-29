package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.cache.TenantCacheInvalidator;
import com.lotus.bixi.upms.api.entity.SysTenant;
import com.lotus.bixi.upms.mapper.SysTenantMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysTenantServiceImplWriteGuardTest {

    @Mock
    SysTenantMapper tenantMapper;

    @Mock
    TenantCacheInvalidator tenantCacheInvalidator;

    @InjectMocks
    SysTenantServiceImpl service;

    @BeforeEach
    void wireBaseMapper() {
        ReflectionTestUtils.setField(service, "baseMapper", tenantMapper);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void saveIsRejectedDuringReadOnlyTenantSwitch() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        SysTenant tenant = validTenant();

        assertThatThrownBy(() -> service.save(tenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tenant_switch_read_only");

        verifyNoInteractions(tenantMapper, tenantCacheInvalidator);
    }

    @Test
    void updateIsRejectedDuringReadOnlyTenantSwitch() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);

        SysTenant tenant = validTenant();
        tenant.setId(2L);
        assertThatThrownBy(() -> service.updateById(tenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tenant_switch_read_only");

        verifyNoInteractions(tenantMapper, tenantCacheInvalidator);
    }

    @Test
    void deleteIsRejectedDuringReadOnlyTenantSwitch() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);

        assertThatThrownBy(() -> service.removeById(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tenant_switch_read_only");

        verifyNoInteractions(tenantMapper, tenantCacheInvalidator);
    }

    @Test
    void statusChangeIsRejectedDuringReadOnlyTenantSwitch() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);

        assertThatThrownBy(() -> service.changeStatus(2L, "1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tenant_switch_read_only");

        verifyNoInteractions(tenantMapper, tenantCacheInvalidator);
    }

    @Test
    void disablingThroughTheGeneralUpdateClearsTenantCaches() {
        SysTenant tenant = validTenant();
        tenant.setId(2L);
        tenant.setStatus("1");
        when(tenantMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.updateById(tenant)).thenReturn(1);

        assertThat(service.updateById(tenant)).isTrue();

        verify(tenantCacheInvalidator).clearTenant(2L);
    }

    @Test
    void deletingATenantClearsTenantCaches() {
        when(tenantMapper.deleteById(2L)).thenReturn(1);

        assertThat(service.removeById(2L)).isTrue();

        verify(tenantCacheInvalidator).clearTenant(2L);
    }

    private SysTenant validTenant() {
        SysTenant tenant = new SysTenant();
        tenant.setName("Tenant");
        tenant.setCode("tenant");
        return tenant;
    }
}
