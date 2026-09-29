package com.lotus.bixi.upms.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Redis pub/sub bridge used to fan out notice refreshes across UPMS instances. */
@Slf4j
public final class NoticeSseRedisBroadcaster implements NoticeSseBroadcaster, MessageListener {

    public static final String TOPIC = "bixi:notice:sse:refresh";

    private final StringRedisTemplate redisTemplate;
    private final NoticeSseEmitterRegistry registry;
    private final ObjectMapper objectMapper;
    private final String nodeId;

    public NoticeSseRedisBroadcaster(StringRedisTemplate redisTemplate,
                                     NoticeSseEmitterRegistry registry,
                                     ObjectMapper objectMapper,
                                     String nodeId) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        this.nodeId = nodeId;
    }

    @Override
    public void publish(NoticeSseRefresh refresh) {
        Objects.requireNonNull(refresh, "refresh");
        try {
            redisTemplate.convertAndSend(TOPIC, objectMapper.writeValueAsString(
                    new WireMessage(refresh.tenantId(), refresh.userId(), refresh.noticeId(),
                            refresh.userNoticeId(), nodeId)));
        }
        catch (Exception failure) {
            // The durable sys_user_notice row remains the source of truth. A Redis outage must
            // not turn a local SSE delivery into a failed notification attempt.
            log.warn("Notice SSE cross-instance broadcast unavailable: {}", failure.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }
        try {
            WireMessage wire = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), WireMessage.class);
            if (nodeId.equals(wire.origin())) {
                return;
            }
            registry.publish(wire.toRefresh());
        }
        catch (Exception failure) {
            log.warn("Ignoring malformed notice SSE broadcast: {}", failure.getMessage());
        }
    }

    private record WireMessage(Long tenantId, Long userId, Long noticeId,
                               Long userNoticeId, String origin) {
        private NoticeSseRefresh toRefresh() {
            return new NoticeSseRefresh(tenantId, userId, noticeId, userNoticeId);
        }
    }
}
