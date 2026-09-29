package com.lotus.bixi.upms.notification;

import com.lotus.bixi.upms.api.constant.NoticeChannel;

/** Provider-neutral adapter for one outbound notification channel. */
public interface NoticeChannelSender {

    NoticeChannel channel();

    /**
     * Lets the dispatcher preserve a disabled provider's explicit failure
     * before it validates recipient-specific data such as a phone or openid.
     */
    default boolean enabled() {
        return true;
    }

    /** Whether this adapter requires a user contact address before invocation. */
    default boolean requiresRecipientAddress() {
        return true;
    }

    NoticeChannelResult send(NoticeChannelRequest request);
}
