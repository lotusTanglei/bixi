package com.lotus.bixi.upms.service.impl;

/**
 * Tenant-scoped notice refresh delivered to local SSE emitters.
 *
 * <p>The tenant is part of the message instead of being inferred by a
 * receiving node. This prevents a cross-node refresh from reaching a user
 * with the same numeric id in another tenant.</p>
 */
public record NoticeSseRefresh(Long tenantId, Long userId, Long noticeId, Long userNoticeId) {

    public NoticeSseRefresh {
        if (tenantId != null && tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive when present");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (noticeId == null || noticeId <= 0) {
            throw new IllegalArgumentException("noticeId must be positive");
        }
        if (userNoticeId == null || userNoticeId <= 0) {
            throw new IllegalArgumentException("userNoticeId must be positive");
        }
    }
}
