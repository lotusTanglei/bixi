package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Generic JSON webhook adapter. */
@Component
public class WebhookNoticeChannelSender extends AbstractHttpNoticeChannelSender {

    private final NoticeChannelProperties properties;

    public WebhookNoticeChannelSender(NoticeChannelProperties properties,
                                      NoticeHttpClient httpClient,
                                      ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.properties = properties;
    }

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.WEBHOOK;
    }

    @Override
    public boolean enabled() {
        return properties.getChannels().getWebhook().isEnabled();
    }

    @Override
    public boolean requiresRecipientAddress() {
        return false;
    }

    @Override
    public NoticeChannelResult send(NoticeChannelRequest request) {
        if (request == null || request.channel() != NoticeChannel.WEBHOOK) {
            return NoticeChannelResult.failed("INVALID_CHANNEL", "webhook request is required");
        }
        Map<String, Object> payload = basePayload(request);
        payload.put("channel", NoticeChannel.WEBHOOK.name());
        return post(properties.getChannels().getWebhook(), request, payload);
    }
}
