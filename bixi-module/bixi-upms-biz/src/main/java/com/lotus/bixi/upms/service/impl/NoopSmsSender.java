package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.upms.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback SMS sender when no provider is configured.
 *
 * @author 唐磊
 */
@Slf4j
@Component
@ConditionalOnMissingBean(value = SmsSender.class, ignored = NoopSmsSender.class)
public class NoopSmsSender implements SmsSender {

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public boolean send(String mobile, String code) {
        log.warn("SMS sender not configured; message not delivered");
        return false;
    }

}
