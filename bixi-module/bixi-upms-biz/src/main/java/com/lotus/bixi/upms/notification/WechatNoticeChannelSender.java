package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provider-neutral JSON WeChat adapter. The address is the verified openid (or
 * mini-program openid) resolved by the dispatcher for the current tenant.
 */
@Component
public class WechatNoticeChannelSender extends AbstractHttpNoticeChannelSender {

    private final NoticeChannelProperties properties;

    @Autowired
    public WechatNoticeChannelSender(NoticeChannelProperties properties,
                                     NoticeHttpClient httpClient,
                                     ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
        this.properties = properties;
    }

    /** Constructor retained for provider-free focused tests. */
    public WechatNoticeChannelSender(NoticeChannelProperties properties) {
        this(properties, null, null);
    }

    @Override
    public NoticeChannel channel() {
        return NoticeChannel.WECHAT;
    }

    @Override
    public boolean enabled() {
        return properties.getChannels().getWechat().isEnabled();
    }

    @Override
    public NoticeChannelResult send(NoticeChannelRequest request) {
        if (request == null || request.channel() != NoticeChannel.WECHAT) {
            return NoticeChannelResult.failed("INVALID_CHANNEL", "WeChat request is required");
        }
        Map<String, Object> payload = basePayload(request);
        payload.put("channel", NoticeChannel.WECHAT.name());
        payload.put("openid", request.address());
        payload.put("text", request.content());
        payload.put("message", request.content());
        return post(properties.getChannels().getWechat(), request, payload);
    }
}
