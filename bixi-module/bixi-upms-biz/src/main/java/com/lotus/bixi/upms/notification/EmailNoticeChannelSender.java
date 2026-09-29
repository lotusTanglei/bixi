package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import org.springframework.stereotype.Component;

import java.util.Map;

/** HTTP email provider adapter. SMTP/JavaMail is intentionally not assumed. */
@Component
public class EmailNoticeChannelSender extends AbstractHttpNoticeChannelSender {

    private final NoticeChannelProperties properties;

    public EmailNoticeChannelSender(NoticeChannelProperties properties,
                                    NoticeHttpClient httpClient,
                                    ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.properties = properties;
    }

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.EMAIL;
    }

    @Override
    public boolean enabled() {
        return properties.getChannels().getEmail().isEnabled();
    }

    @Override
    public NoticeChannelResult send(NoticeChannelRequest request) {
        if (request == null || request.channel() != NoticeChannel.EMAIL) {
            return NoticeChannelResult.failed("INVALID_CHANNEL", "email request is required");
        }
        Map<String, Object> payload = basePayload(request);
        payload.put("from", properties.getChannels().getEmail().getFrom());
        payload.put("to", request.address());
        payload.put("subject", request.title());
        payload.put("text", request.content());
        payload.put("html", request.content());
        return post(properties.getChannels().getEmail(), request, payload);
    }
}
