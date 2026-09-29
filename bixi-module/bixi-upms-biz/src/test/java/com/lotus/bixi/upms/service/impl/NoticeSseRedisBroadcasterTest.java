package com.lotus.bixi.upms.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeSseRedisBroadcasterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void localEmitterRegistrySeparatesSameUserAcrossTenants() {
        NoticeSseEmitterRegistry registry = new NoticeSseEmitterRegistry();

        com.lotus.bixi.common.core.context.TenantContextHolder.set(42L);
        registry.subscribe(7L);
        com.lotus.bixi.common.core.context.TenantContextHolder.set(43L);
        registry.subscribe(7L);

        assertThat(registry.activeEmitterCount(42L, 7L)).isOne();
        assertThat(registry.activeEmitterCount(43L, 7L)).isOne();
    }

    @Test
    void publishesTenantAndUserBoundPayloadAndIgnoresItsOwnEcho() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        NoticeSseRedisBroadcaster broadcaster = new NoticeSseRedisBroadcaster(
                redis, registry, objectMapper, "node-a");
        NoticeSseRefresh refresh = new NoticeSseRefresh(42L, 7L, 11L, 13L);

        broadcaster.publish(refresh);

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(org.mockito.ArgumentMatchers.eq(NoticeSseRedisBroadcaster.TOPIC),
                payload.capture());
        assertThat(payload.getValue()).contains("\"tenantId\":42", "\"userId\":7",
                "\"noticeId\":11", "\"userNoticeId\":13", "\"origin\":\"node-a\"");

        broadcaster.onMessage(new DefaultMessage(NoticeSseRedisBroadcaster.TOPIC.getBytes(StandardCharsets.UTF_8),
                payload.getValue().getBytes(StandardCharsets.UTF_8)), null);
        verifyNoInteractions(registry);
    }

    @Test
    void forwardsRemoteTenantScopedPayloadToLocalEmitters() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        NoticeSseRedisBroadcaster broadcaster = new NoticeSseRedisBroadcaster(
                redis, registry, objectMapper, "node-b");
        String payload = "{\"tenantId\":42,\"userId\":7,\"noticeId\":11,"
                + "\"userNoticeId\":13,\"origin\":\"node-a\"}";

        broadcaster.onMessage(new DefaultMessage(NoticeSseRedisBroadcaster.TOPIC.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)), null);

        verify(registry).publish(new NoticeSseRefresh(42L, 7L, 11L, 13L));
    }

    @Test
    void keepsLocalDeliverySuccessfulWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        NoticeSseEmitterRegistry registry = mock(NoticeSseEmitterRegistry.class);
        doThrow(new IllegalStateException("redis down")).when(redis)
                .convertAndSend(anyString(), anyString());
        NoticeSseRedisBroadcaster broadcaster = new NoticeSseRedisBroadcaster(
                redis, registry, objectMapper, "node-a");

        assertThatCode(() -> broadcaster.publish(new NoticeSseRefresh(42L, 7L, 11L, 13L)))
                .doesNotThrowAnyException();
    }
}
