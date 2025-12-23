package com.lotus.bixi.upms.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class UserNoticeSseService {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitterMap.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError((e) -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("open").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }

        return emitter;
    }

    public void publishRefresh(Long userId, Long noticeId, Long userNoticeId) {
        List<SseEmitter> emitters = emitterMap.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "type", "notice",
                "noticeId", noticeId,
                "userNoticeId", userNoticeId
        );

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notice")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                remove(userId, emitter);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emitterMap.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emitterMap.remove(userId);
        }
    }
}

