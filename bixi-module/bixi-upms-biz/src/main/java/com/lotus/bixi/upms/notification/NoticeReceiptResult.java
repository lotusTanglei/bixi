package com.lotus.bixi.upms.notification;

/** Outcome returned by the idempotent provider receipt processor. */
public record NoticeReceiptResult(
        boolean accepted,
        boolean updated,
        String receiptStatus,
        String code,
        String message) {

    public static NoticeReceiptResult updated(String status, String code) {
        return new NoticeReceiptResult(true, true, status, code, null);
    }

    public static NoticeReceiptResult duplicate(String status, String code) {
        return new NoticeReceiptResult(true, false, status, code, "receipt already applied");
    }

    public static NoticeReceiptResult rejected(String code, String message) {
        return new NoticeReceiptResult(false, false, null, code, message);
    }
}
