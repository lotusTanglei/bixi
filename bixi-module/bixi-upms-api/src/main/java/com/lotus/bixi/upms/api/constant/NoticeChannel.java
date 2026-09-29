package com.lotus.bixi.upms.api.constant;

import java.util.Locale;

/**
 * Outbound channel selected for a published notice.
 *
 * <p>The value is part of the UPMS API contract so cloud and single use the
 * same persisted and MQ representation.  The external providers themselves
 * remain business-module adapters.</p>
 */
public enum NoticeChannel {
    IN_APP,
    EMAIL,
    SMS,
    WECHAT,
    WEBHOOK;

    /**
     * Parse an API/configuration value. Missing values intentionally default
     * to the existing in-app SSE delivery path.
     */
    public static NoticeChannel parse(String value) {
        if (value == null || value.isBlank()) {
            return IN_APP;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "INAPP", "IN_APP", "SSE" -> IN_APP;
            case "MAIL", "EMAIL" -> EMAIL;
            case "SMS", "TEXT" -> SMS;
            case "WECHAT", "WE_CHAT", "WX", "WEIXIN" -> WECHAT;
            case "WEBHOOK", "WEB_HOOK", "HTTP" -> WEBHOOK;
            default -> throw new IllegalArgumentException("Unsupported notice channel: " + value);
        };
    }
}
