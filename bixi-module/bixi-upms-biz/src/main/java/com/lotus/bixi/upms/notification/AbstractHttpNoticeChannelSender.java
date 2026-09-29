package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.constant.NoticeChannel;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

abstract class AbstractHttpNoticeChannelSender implements NoticeChannelSender {

    private final NoticeHttpClient httpClient;
    private final ObjectMapper objectMapper;

    protected AbstractHttpNoticeChannelSender(NoticeHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    protected final NoticeChannelResult post(NoticeChannelProperties.HttpChannel properties,
                                             NoticeChannelRequest request,
                                             Map<String, Object> payload) {
        if (!properties.isEnabled()) {
            return NoticeChannelResult.failed("DISABLED", channel() + " delivery is disabled");
        }
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return NoticeChannelResult.failed("NOT_CONFIGURED", channel() + " endpoint is not configured");
        }
        URI uri;
        try {
            uri = URI.create(endpoint.trim());
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isBlank()) {
                return NoticeChannelResult.failed("NOT_CONFIGURED", channel() + " endpoint must be an HTTP(S) URL");
            }
        } catch (IllegalArgumentException invalidEndpoint) {
            return NoticeChannelResult.failed("NOT_CONFIGURED", channel() + " endpoint is invalid");
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException failure) {
            return NoticeChannelResult.failed("PAYLOAD_SERIALIZATION_FAILED", "notice payload could not be encoded");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            headers.put("X-Api-Key", properties.getApiKey());
        }
        if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
            headers.put("Authorization", "Bearer " + properties.getBearerToken());
        }
        long timeoutMillis = Math.max(1_000L, Math.min(120_000L, properties.getTimeoutMillis()));
        try {
            NoticeHttpResponse response = httpClient.post(uri, body, headers, Duration.ofMillis(timeoutMillis));
            if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                return NoticeChannelResult.success();
            }
            int status = response == null ? 0 : response.statusCode();
            return NoticeChannelResult.failed("HTTP_" + status, channel() + " provider returned HTTP " + status);
        } catch (Exception failure) {
            return NoticeChannelResult.failed("HTTP_ERROR", channel() + " provider request failed: " + summarize(failure));
        }
    }

    protected static Map<String, Object> basePayload(NoticeChannelRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", request.tenantId());
        payload.put("noticeId", request.noticeId());
        payload.put("recipientId", request.recipientId());
        // Provider callbacks use this stable recipient-row id. Keep recipientId
        // as the user id for compatibility with existing provider contracts.
        payload.put("userNoticeId", request.userNoticeId());
        payload.put("title", request.title());
        payload.put("content", request.content());
        return payload;
    }

    private static String summarize(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        message = message.trim();
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

    public abstract NoticeChannel channel();
}
