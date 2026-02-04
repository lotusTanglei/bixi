package com.lotus.bixi.upms.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SysUserNoticeSseService {

    SseEmitter subscribe(Long userId);

    void publishRefresh(Long userId, Long noticeId, Long userNoticeId);
}
