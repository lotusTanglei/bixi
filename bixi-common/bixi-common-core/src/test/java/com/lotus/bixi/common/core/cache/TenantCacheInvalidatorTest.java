package com.lotus.bixi.common.core.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantCacheInvalidatorTest {

    @Test
    void scanPatternContainsOnlyTheRequestedTenantNamespace() {
        assertThat(TenantCacheInvalidator.scanPattern(7L))
                .isEqualTo("*TENANT:7:*");
    }
}
