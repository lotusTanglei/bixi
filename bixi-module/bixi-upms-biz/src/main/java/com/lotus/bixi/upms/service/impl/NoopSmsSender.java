package com.lotus.bixi.upms.service.impl;

import com.lotus.bixi.upms.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op SMS sender. Logs the code for development/testing purposes.
 * Replace with a real implementation for production use.
 *
 * @author 唐磊
 */
@Slf4j
@Component
@ConditionalOnMissingBean(value = SmsSender.class, ignored = NoopSmsSender.class)
public class NoopSmsSender implements SmsSender {

    @Override
    public boolean send(String mobile, String code) {
        log.warn("SMS sender not configured — code {} for {} logged but NOT delivered", code, mobile);
        return false;
    }

}
