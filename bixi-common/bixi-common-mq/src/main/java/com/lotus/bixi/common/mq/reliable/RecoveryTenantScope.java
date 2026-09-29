package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;

/** Tenant boundary shared by operator-facing durable recovery services. */
public final class RecoveryTenantScope {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static boolean isVisible(DurableMessage message) {
        if (TenantContextHolder.isAllTenantsReadOnly()) {
            return true;
        }
        if (message == null) {
            return false;
        }
        try {
            JsonNode value = JSON.readTree(message.payloadJson()).get("tenantScope");
            if (value == null || !value.isTextual() || value.textValue().isBlank()) {
                return false;
            }
            String scope = value.textValue();
            return currentScope().equals(scope)
                    || currentTenantId().equals(SecurityConstants.DEFAULT_TENANT_ID) && "default".equals(scope);
        }
        catch (Exception invalidPayload) {
            return false;
        }
    }

    public static void requireVisible(DurableMessage message) {
        if (!isVisible(message)) {
            throw new IllegalArgumentException("Recovery resource does not belong to current tenant");
        }
    }

    public static void requireWritable() {
        if (TenantContextHolder.isAllTenantsReadOnly()) {
            throw new IllegalStateException("all_tenants_read_only");
        }
        TenantContextHolder.assertWritable();
    }

    public static Long currentTenantId() {
        Long tenantId = TenantContextHolder.get();
        return tenantId == null ? SecurityConstants.DEFAULT_TENANT_ID : tenantId;
    }

    public static String currentScope() {
        return currentTenantId().toString();
    }

    private RecoveryTenantScope() {
    }
}
