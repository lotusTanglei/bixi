package com.lotus.bixi.common.mq.reliable;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecoveryTenantScopeTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void matchesCurrentTenantAndDefaultTenantAlias() {
        TenantContextHolder.set(41L);
        assertThat(RecoveryTenantScope.isVisible(message("41"))).isTrue();
        assertThat(RecoveryTenantScope.isVisible(message("42"))).isFalse();

        TenantContextHolder.set(1L);
        assertThat(RecoveryTenantScope.isVisible(message("default"))).isTrue();
        assertThat(RecoveryTenantScope.isVisible(message("1"))).isTrue();
    }

    @Test
    void allTenantReadScopeCanSeeEveryMessageButCannotMutate() {
        TenantContextHolder.set(1L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);

        assertThat(RecoveryTenantScope.isVisible(message("42"))).isTrue();
        assertThatThrownBy(RecoveryTenantScope::requireWritable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("all_tenants_read_only");
    }

    @Test
    void tenantSwitchCannotMutate() {
        TenantContextHolder.set(42L);
        TenantContextHolder.setReadOnlySwitch(true);

        assertThatThrownBy(RecoveryTenantScope::requireWritable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("tenant_switch_read_only");
    }

    @Test
    void ordinaryTenantFailsClosedForMissingBlankOrInvalidPayloadScope() {
        TenantContextHolder.set(41L);
        DurableMessage missing = rawMessage("{}");
        DurableMessage blank = rawMessage("{\"tenantScope\":\"\"}");
        DurableMessage invalid = rawMessage("{\"tenantScope\":\"tenant-41\"}");
        DurableMessage numeric = rawMessage("{\"tenantScope\":41}");
        DurableMessage malformed = mock(DurableMessage.class);
        when(malformed.payloadJson()).thenReturn("{not-json");

        assertThat(RecoveryTenantScope.isVisible(missing)).isFalse();
        assertThat(RecoveryTenantScope.isVisible(blank)).isFalse();
        assertThat(RecoveryTenantScope.isVisible(invalid)).isFalse();
        assertThat(RecoveryTenantScope.isVisible(numeric)).isFalse();
        assertThat(RecoveryTenantScope.isVisible(malformed)).isFalse();
        assertThatThrownBy(() -> RecoveryTenantScope.requireVisible(missing))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DurableMessage message(String tenantScope) {
        return rawMessage("{\"tenantScope\":\"" + tenantScope + "\"}");
    }

    private static DurableMessage rawMessage(String payload) {
        return DurableMessage.create("upms", "workflow", "00000000-0000-0000-0000-000000000041",
                "WORKFLOW_TEST", 1, payload);
    }
}
