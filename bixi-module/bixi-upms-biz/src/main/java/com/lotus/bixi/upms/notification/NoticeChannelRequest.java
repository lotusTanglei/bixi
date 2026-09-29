package com.lotus.bixi.upms.notification;

import com.lotus.bixi.upms.api.constant.NoticeChannel;

/** Immutable, transport-neutral input passed to one channel adapter. */
public record NoticeChannelRequest(
        NoticeChannel channel,
        Long tenantId,
        Long noticeId,
        Long recipientId,
        String title,
        String content,
        String address,
        Long userNoticeId) {

    public NoticeChannelRequest(NoticeChannel channel, Long tenantId, Long noticeId,
                                Long recipientId, String title, String content) {
        this(channel, tenantId, noticeId, recipientId, title, content, null, null);
    }

    public NoticeChannelRequest(NoticeChannel channel, Long tenantId, Long noticeId,
                                Long recipientId, String title, String content, String address) {
        this(channel, tenantId, noticeId, recipientId, title, content, address, null);
    }

    public NoticeChannelRequest(NoticeChannel channel, Long tenantId, Long noticeId,
                                String title, String content) {
        this(channel, tenantId, noticeId, null, title, content, null, null);
    }

    /**
     * Correlates an asynchronous provider receipt with the recipient row. The
     * existing constructors intentionally keep this optional for old callers.
     */
    public NoticeChannelRequest withUserNoticeId(Long id) {
        return new NoticeChannelRequest(channel, tenantId, noticeId, recipientId,
                title, content, address, id);
    }
}
