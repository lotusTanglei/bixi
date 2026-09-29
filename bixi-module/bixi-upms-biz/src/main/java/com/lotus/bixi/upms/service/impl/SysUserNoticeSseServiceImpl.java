package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import com.lotus.bixi.upms.service.SysUserNoticeSseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SysUserNoticeSseServiceImpl implements SysUserNoticeSseService {

    private final NoticeSseEmitterRegistry registry;
    private final NoticeSseBroadcaster broadcaster;
    private final SysUserNoticeMapper userNoticeMapper;

    private static final int MAX_REPLAY_NOTICES = 100;

    /** Constructor retained for provider-free focused tests and local-only deployments. */
    public SysUserNoticeSseServiceImpl() {
        this(new NoticeSseEmitterRegistry(), refresh -> {
        }, null);
    }

    @Autowired
    public SysUserNoticeSseServiceImpl(NoticeSseEmitterRegistry registry,
                                       ObjectProvider<NoticeSseBroadcaster> broadcasterProvider,
                                       SysUserNoticeMapper userNoticeMapper) {
        this(registry, broadcasterProvider.getIfAvailable(() -> refresh -> {
        }), userNoticeMapper);
    }

    public SysUserNoticeSseServiceImpl(NoticeSseEmitterRegistry registry,
                                       NoticeSseBroadcaster broadcaster) {
        this(registry, broadcaster, null);
    }

    public SysUserNoticeSseServiceImpl(NoticeSseEmitterRegistry registry,
                                       NoticeSseBroadcaster broadcaster,
                                       SysUserNoticeMapper userNoticeMapper) {
        this.registry = registry;
        this.broadcaster = broadcaster;
        this.userNoticeMapper = userNoticeMapper;
    }

    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = registry.subscribe(userId);
        if (userNoticeMapper == null) {
            return emitter;
        }
        boolean previousReadOnly = TenantContextHolder.isReadOnlySwitch();
        boolean previousAllTenantsReadOnly = TenantContextHolder.isAllTenantsReadOnly();
        try {
            // An SSE stream is always personal. Even a super-admin request that
            // arrived with the explicit ALL tenant read-only scope must not turn
            // a user id into a cross-tenant replay feed.
            TenantContextHolder.setReadOnlySwitch(false);
            TenantContextHolder.setAllTenantsReadOnly(false);
            List<SysUserNotice> rows = userNoticeMapper.selectUnreadPublishedForSse(userId, MAX_REPLAY_NOTICES);
            if (rows == null || rows.isEmpty()) {
                return emitter;
            }
            Long tenantId = TenantContextHolder.get();
            List<NoticeSseRefresh> replay = new ArrayList<>(rows.size());
            for (SysUserNotice row : rows) {
                if (row == null || !userId.equals(row.getUserId())) {
                    continue;
                }
                try {
                    replay.add(new NoticeSseRefresh(tenantId, row.getUserId(), row.getNoticeId(), row.getId()));
                }
                catch (IllegalArgumentException invalidRow) {
                    log.warn("Ignoring malformed notice SSE replay row for user {}: {}", userId,
                            invalidRow.getMessage());
                }
            }
            registry.replay(emitter, userId, replay);
        }
        catch (RuntimeException failure) {
            // The durable page remains available if a reconnect races a database
            // outage; an SSE connection should not fail solely because replay is
            // best-effort.
            log.warn("Notice SSE replay unavailable for user {}: {}", userId, failure.getMessage());
        }
        finally {
            TenantContextHolder.setReadOnlySwitch(previousReadOnly);
            TenantContextHolder.setAllTenantsReadOnly(previousAllTenantsReadOnly);
        }
        return emitter;
    }

    @Override
    public void publishRefresh(Long userId, Long noticeId, Long userNoticeId) {
        NoticeSseRefresh refresh = new NoticeSseRefresh(TenantContextHolder.get(), userId, noticeId,
                userNoticeId);
        registry.publish(refresh);
        try {
            broadcaster.publish(refresh);
        }
        catch (RuntimeException failure) {
            // A local emitter was already refreshed; Redis is only a best-effort cross-node hint.
            log.warn("Notice SSE cross-instance broadcast failed: {}", failure.getMessage());
        }
    }
}
