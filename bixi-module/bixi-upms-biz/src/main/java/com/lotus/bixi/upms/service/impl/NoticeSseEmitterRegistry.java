package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process SSE emitter registry. Cross-instance fan-out is handled separately by Redis. */
@Component
public class NoticeSseEmitterRegistry {

    private final Map<EmitterKey, CopyOnWriteArrayList<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        EmitterKey key = new EmitterKey(TenantContextHolder.get(), userId);
        SseEmitter emitter = new SseEmitter(0L);
        emitterMap.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(ignored -> remove(key, emitter));

        try {
            emitter.send(SseEmitter.event().name("open").data("ok"));
        }
        catch (IOException | IllegalStateException failure) {
            remove(key, emitter);
        }
        return emitter;
    }

    /**
     * Replays persisted notices only to the newly established connection.
     * Calling {@link #publish(NoticeSseRefresh)} here would duplicate the
     * backlog to every already-connected browser for the same user.
     */
    void replay(SseEmitter emitter, Long userId, Collection<NoticeSseRefresh> refreshes) {
        if (emitter == null || userId == null || userId <= 0 || refreshes == null || refreshes.isEmpty()) {
            return;
        }
        EmitterKey key = new EmitterKey(TenantContextHolder.get(), userId);
        List<SseEmitter> emitters = emitterMap.get(key);
        if (emitters == null || !emitters.contains(emitter)) {
            return;
        }
        for (NoticeSseRefresh refresh : refreshes) {
            if (refresh == null || !key.equals(new EmitterKey(refresh.tenantId(), refresh.userId()))) {
                continue;
            }
            if (!send(key, emitter, refresh)) {
                break;
            }
        }
    }

    /** Sends only to emitters with the same tenant and user identity. */
    public void publish(NoticeSseRefresh refresh) {
        Objects.requireNonNull(refresh, "refresh");
        EmitterKey key = new EmitterKey(refresh.tenantId(), refresh.userId());
        List<SseEmitter> emitters = emitterMap.get(key);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "type", "notice",
                "noticeId", refresh.noticeId(),
                "userNoticeId", refresh.userNoticeId());
        for (SseEmitter emitter : emitters) {
            send(key, emitter, refresh, payload);
        }
    }

    private boolean send(EmitterKey key, SseEmitter emitter, NoticeSseRefresh refresh) {
        Map<String, Object> payload = Map.of(
                "type", "notice",
                "noticeId", refresh.noticeId(),
                "userNoticeId", refresh.userNoticeId());
        return send(key, emitter, refresh, payload);
    }

    private boolean send(EmitterKey key, SseEmitter emitter, NoticeSseRefresh refresh,
                         Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(refresh.userNoticeId()))
                    .data(payload, MediaType.APPLICATION_JSON));
            return true;
        }
        catch (IOException | IllegalStateException failure) {
            remove(key, emitter);
            return false;
        }
    }

    private void remove(EmitterKey key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(key);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        // Re-check and remove atomically so a concurrent subscribe cannot be lost
        // between the empty check and map removal.
        emitterMap.computeIfPresent(key, (ignored, current) -> current.isEmpty() ? null : current);
    }

    int activeEmitterCount(Long tenantId, Long userId) {
        List<SseEmitter> emitters = emitterMap.get(new EmitterKey(tenantId, userId));
        return emitters == null ? 0 : emitters.size();
    }

    private record EmitterKey(Long tenantId, Long userId) {
    }
}
