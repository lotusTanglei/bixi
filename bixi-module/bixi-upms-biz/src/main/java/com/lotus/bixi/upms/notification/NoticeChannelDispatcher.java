package com.lotus.bixi.upms.notification;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves a recipient inside the current tenant and invokes exactly one
 * configured channel adapter.  It deliberately returns failures instead of
 * silently falling back to another channel, making retries auditable.
 */
@Slf4j
@Component
public class NoticeChannelDispatcher {

    private final Map<NoticeChannel, NoticeChannelSender> senders;
    private final SysUserService userService;

    public NoticeChannelDispatcher(List<NoticeChannelSender> channelSenders, SysUserService userService) {
        EnumMap<NoticeChannel, NoticeChannelSender> map = new EnumMap<>(NoticeChannel.class);
        if (channelSenders != null) {
            for (NoticeChannelSender sender : channelSenders) {
                if (sender != null && sender.channel() != null) {
                    map.put(sender.channel(), sender);
                }
            }
        }
        this.senders = Map.copyOf(map);
        this.userService = userService;
    }

    public NoticeChannelResult dispatch(NoticeChannelRequest request, Long recipientId) {
        if (request == null || request.channel() == null) {
            return NoticeChannelResult.failed("INVALID_CHANNEL", "notice channel is required");
        }
        if (request.tenantId() == null || request.tenantId() <= 0) {
            return NoticeChannelResult.failed("TENANT_NOT_SET", "notice tenant is required");
        }
        Long currentTenant = TenantContextHolder.get();
        if (currentTenant == null || !currentTenant.equals(request.tenantId())) {
            return NoticeChannelResult.failed("TENANT_MISMATCH", "notice tenant does not match the current context");
        }
        if (request.channel() == NoticeChannel.IN_APP) {
            return NoticeChannelResult.failed("IN_APP_HANDLED_BY_SSE", "in-app notices use the SSE notifier");
        }
        if (recipientId == null || userService == null) {
            return NoticeChannelResult.failed("RECIPIENT_NOT_FOUND", "notice recipient is unavailable");
        }

        SysUser recipient;
        try {
            recipient = userService.getById(recipientId);
        } catch (RuntimeException failure) {
            log.warn("Could not resolve notice recipient {}", recipientId, failure);
            return NoticeChannelResult.failed("RECIPIENT_LOOKUP_FAILED", summarize(failure));
        }
        if (recipient == null) {
            return NoticeChannelResult.failed("RECIPIENT_NOT_FOUND", "notice recipient is unavailable");
        }
        if (recipient.getTenantId() != null && !request.tenantId().equals(recipient.getTenantId())) {
            return NoticeChannelResult.failed("TENANT_MISMATCH", "notice recipient belongs to another tenant");
        }

        NoticeChannelSender sender = senders.get(request.channel());
        if (sender == null) {
            return NoticeChannelResult.failed("CHANNEL_UNAVAILABLE", "notice channel adapter is unavailable");
        }
        if (!sender.enabled()) {
            return sender.send(new NoticeChannelRequest(request.channel(), request.tenantId(), request.noticeId(),
                    recipientId, request.title(), request.content(), null, request.userNoticeId()));
        }
        String address = address(request.channel(), recipient);
        if (sender.requiresRecipientAddress() && (address == null || address.isBlank())) {
            return NoticeChannelResult.failed("RECIPIENT_ADDRESS_MISSING",
                    "recipient has no " + request.channel().name().toLowerCase(Locale.ROOT) + " address");
        }
        try {
            return sender.send(new NoticeChannelRequest(request.channel(), request.tenantId(), request.noticeId(),
                    recipientId, request.title(), request.content(), address, request.userNoticeId()));
        } catch (RuntimeException failure) {
            log.warn("Notice {} delivery failed for recipient {}", request.channel(), recipientId, failure);
            return NoticeChannelResult.failed("SEND_EXCEPTION", summarize(failure));
        }
    }

    private static String address(NoticeChannel channel, SysUser recipient) {
        return switch (channel) {
            case EMAIL -> recipient.getEmail();
            case SMS -> recipient.getPhone();
            case WECHAT -> firstNonBlank(recipient.getWxOpenid(), recipient.getMiniOpenid());
            case WEBHOOK, IN_APP -> null;
        };
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String summarize(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        message = message.trim();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
