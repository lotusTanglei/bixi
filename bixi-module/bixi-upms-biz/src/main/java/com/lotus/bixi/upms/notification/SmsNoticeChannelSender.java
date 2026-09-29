package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provider-neutral JSON SMS adapter. A deployment supplies the endpoint and
 * credentials for its chosen SMS gateway; the delivery state machine remains
 * provider independent and records non-2xx responses as failures.
 */
@Component
public class SmsNoticeChannelSender extends AbstractHttpNoticeChannelSender {

    private final NoticeChannelProperties properties;

    @Autowired
    public SmsNoticeChannelSender(NoticeChannelProperties properties,
                                  NoticeHttpClient httpClient,
                                  ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.properties = properties;
    }

    /** Constructor retained for provider-free focused tests. */
    public SmsNoticeChannelSender(NoticeChannelProperties properties) {
        this(properties, null, null);
    }

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.SMS;
    }

    @Override
    public boolean enabled() {
        return properties.getChannels().getSms().isEnabled();
    }

    @Override
    public NoticeChannelResult send(NoticeChannelRequest request) {
        if (request == null || request.channel() != NoticeChannel.SMS) {
            return NoticeChannelResult.failed("INVALID_CHANNEL", "SMS request is required");
        }
        Map<String, Object> payload = basePayload(request);
        payload.put("channel", NoticeChannel.SMS.name());
        payload.put("to", request.address());
        payload.put("text", request.content());
        payload.put("message", request.content());
        return post(properties.getChannels().getSms(), request, payload);
    }
}
