package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/** Trusted identity helpers shared by the AI business services. */
final class AiOwnershipSupport {

    private AiOwnershipSupport() {
    }

    static BixiUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        BixiUser user = SecurityUtils.getUser();
        if (authentication == null || !authentication.isAuthenticated()
                || user == null || user.getId() == null || user.getTenantId() == null) {
            throw new AccessDeniedException("请先登录");
        }
        return user;
    }

    /**
     * Return the tenant selected for this request. The request filter changes
     * this context for a super-admin tenant view, while direct service tests
     * may only install the tenant carried by the authenticated principal.
     */
    static Long tenantId() {
        BixiUser user = requireUser();
        Long activeTenantId = TenantContextHolder.get();
        if (activeTenantId == null || Objects.equals(activeTenantId, user.getTenantId())) {
            return activeTenantId != null ? activeTenantId : user.getTenantId();
        }
        // A different tenant is only valid for the platform-admin read-only
        // views established by TenantContextFilter. Direct service calls must
        // not be able to swap the tenant bucket by mutating the thread-local.
        if (TenantContextHolder.isReadOnlySwitch() || TenantContextHolder.isAllTenantsReadOnly()) {
            return activeTenantId;
        }
        throw new AccessDeniedException("租户上下文与当前用户不匹配");
    }

    static void requireWritable() {
        requireUser();
        if (TenantContextHolder.isAllTenantsReadOnly()) {
            throw new IllegalStateException("all_tenants_read_only");
        }
        TenantContextHolder.assertWritable();
    }

    static IllegalArgumentException missing(String resource) {
        return new IllegalArgumentException(resource + "不存在");
    }
}
