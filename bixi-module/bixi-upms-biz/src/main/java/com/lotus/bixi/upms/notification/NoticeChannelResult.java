package com.lotus.bixi.upms.notification;

/** Result persisted by the caller as the recipient delivery outcome. */
public record NoticeChannelResult(boolean delivered, String code, String message) {

    public static NoticeChannelResult success() {
        return new NoticeChannelResult(true, "DELIVERED", null);
    }

    public static NoticeChannelResult failed(String code, String message) {
        return new NoticeChannelResult(false, code, message);
    }
}
