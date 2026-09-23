package com.lotus.bixi.common.log.util;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysLogTenantContextTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void localLogEventCarriesTheTrustedTenantIdForAsyncPersistence() {
        TenantContextHolder.set(9L);

        assertThat(SysLogUtils.getSysLog().getTenantId()).isEqualTo(9L);
    }
}
