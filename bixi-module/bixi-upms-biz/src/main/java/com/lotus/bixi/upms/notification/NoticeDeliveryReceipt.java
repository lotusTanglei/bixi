package com.lotus.bixi.upms.notification;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * Provider-neutral asynchronous delivery receipt. Providers only need to
 * return the identifiers and a coarse status; raw provider payloads are never
 * persisted by the application.
 */
@Data
public class NoticeDeliveryReceipt {

    private Long tenantId;

    private Long noticeId;

    @JsonAlias({"deliveryId", "delivery_id", "user_notice_id"})
    private Long userNoticeId;

    /** User id used by older provider integrations when userNoticeId is absent. */
    @JsonAlias({"recipient_id", "recipientUserId"})
    private Long recipientId;

    private String status;

    /** Vendor status code. The service stores only a bounded, safe code. */
    @JsonAlias({"statusCode", "status_code", "errorCode", "error_code"})
    private String code;
}
