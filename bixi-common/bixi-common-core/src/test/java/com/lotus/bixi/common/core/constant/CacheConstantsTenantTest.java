package com.lotus.bixi.common.core.constant;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConstantsTenantTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void namespacesKeysByTenantAndKeepsTokenPrefixUnchanged() {
        assertThat(CacheConstants.tenantKey(CacheConstants.USER_DETAILS, 7L))
                .isEqualTo("user_details:TENANT:7:");
        TenantContextHolder.set(8L);
        assertThat(CacheConstants.currentTenantKey(CacheConstants.DICT_DETAILS))
                .isEqualTo("dict_details:TENANT:8:");
        assertThat(CacheConstants.PROJECT_OAUTH_ACCESS).isEqualTo("token::access_token");
    }
}
