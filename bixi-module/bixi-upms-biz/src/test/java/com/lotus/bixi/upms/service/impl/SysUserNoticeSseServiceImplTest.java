package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class SysUserNoticeSseServiceImplTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void publishesTenantScopedRefreshToTheCrossInstanceBroadcaster() {
        TenantContextHolder.set(42L);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        NoticeSseBroadcaster broadcaster = mock(NoticeSseBroadcaster.class);
        SysUserNoticeSseServiceImpl service = new SysUserNoticeSseServiceImpl(registry, broadcaster);

        service.publishRefresh(7L, 11L, 13L);

        NoticeSseRefresh refresh = new NoticeSseRefresh(42L, 7L, 11L, 13L);
        verify(registry).publish(refresh);
        verify(broadcaster).publish(refresh);
    }

    @Test
    void keepsTheLocalEmitterPathSuccessfulWhenCrossInstanceBroadcastFails() {
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        NoticeSseBroadcaster broadcaster = refresh -> {
            throw new IllegalStateException("redis down");
        };
        SysUserNoticeSseServiceImpl service = new SysUserNoticeSseServiceImpl(registry, broadcaster);

        TenantContextHolder.set(42L);
        assertThatCode(() -> service.publishRefresh(7L, 11L, 13L))
                .doesNotThrowAnyException();
        verify(registry).publish(new NoticeSseRefresh(42L, 7L, 11L, 13L));
    }

    @Test
    void ignoresAnEmitterThatCompletedBeforeARefreshWasPublished() {
        SysUserNoticeSseServiceImpl service = new SysUserNoticeSseServiceImpl();
        SseEmitter emitter = service.subscribe(7L);
        emitter.complete();

        assertThatCode(() -> service.publishRefresh(7L, 11L, 13L))
                .doesNotThrowAnyException();
    }

    @Test
    void replaysUnreadRowsOnlyToTheNewlyConnectedEmitter() {
        TenantContextHolder.set(42L);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        NoticeSseBroadcaster broadcaster = refresh -> {
        };
        SysUserNoticeMapper mapper = mock(SysUserNoticeMapper.class);
        SseEmitter emitter = new SseEmitter();
        SysUserNotice row = new SysUserNotice();
        row.setId(13L);
        row.setNoticeId(11L);
        row.setUserId(7L);
        when(registry.subscribe(7L)).thenReturn(emitter);
        when(mapper.selectUnreadPublishedForSse(7L, 100)).thenReturn(List.of(row));

        SysUserNoticeSseServiceImpl service = new SysUserNoticeSseServiceImpl(registry, broadcaster, mapper);

        service.subscribe(7L);

        verify(registry).replay(emitter, 7L,
                List.of(new NoticeSseRefresh(42L, 7L, 11L, 13L)));
    }

    @Test
    void keepsTheConnectionWhenReplayLookupFails() {
        TenantContextHolder.set(42L);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        SysUserNoticeMapper mapper = mock(SysUserNoticeMapper.class);
        SseEmitter emitter = new SseEmitter();
        when(registry.subscribe(7L)).thenReturn(emitter);
        when(mapper.selectUnreadPublishedForSse(7L, 100)).thenThrow(new IllegalStateException("db down"));

        SysUserNoticeSseServiceImpl service = new SysUserNoticeSseServiceImpl(registry,
                refresh -> { }, mapper);

        assertThatCode(() -> service.subscribe(7L)).doesNotThrowAnyException();
        verify(registry).subscribe(7L);
    }

    @Test
    void forcesPersonalTenantScopeDuringReplayAndRestoresAdminScope() {
        TenantContextHolder.set(42L);
        TenantContextHolder.setReadOnlySwitch(true);
        TenantContextHolder.setAllTenantsReadOnly(true);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        SysUserNoticeMapper mapper = mock(SysUserNoticeMapper.class);
        SseEmitter emitter = new SseEmitter();
        when(registry.subscribe(7L)).thenReturn(emitter);
        doAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(TenantContextHolder.isReadOnlySwitch()).isFalse();
            org.assertj.core.api.Assertions.assertThat(TenantContextHolder.isAllTenantsReadOnly()).isFalse();
            return List.of();
        }).when(mapper).selectUnreadPublishedForSse(7L, 100);

        SysUserNoticeSseServiceImpl service = new SysUserNoticeSseServiceImpl(registry,
                refresh -> { }, mapper);

        service.subscribe(7L);

        org.assertj.core.api.Assertions.assertThat(TenantContextHolder.isReadOnlySwitch()).isTrue();
        org.assertj.core.api.Assertions.assertThat(TenantContextHolder.isAllTenantsReadOnly()).isTrue();
    }
}
