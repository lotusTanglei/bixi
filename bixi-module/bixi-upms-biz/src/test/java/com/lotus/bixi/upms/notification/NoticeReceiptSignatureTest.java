package com.lotus.bixi.upms.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeReceiptSignatureTest {

    @Test
    void verifiesRawBodyAndSupportsSha256Prefix() {
        String body = "{\"tenantId\":7,\"status\":\"delivered\"}";
        String signature = NoticeReceiptSignature.sign(body, "receipt-secret");

        assertThat(NoticeReceiptSignature.verify(body, signature, "receipt-secret")).isTrue();
        assertThat(NoticeReceiptSignature.verify(body, "sha256=" + signature, "receipt-secret")).isTrue();
        assertThat(NoticeReceiptSignature.verify(body + " ", signature, "receipt-secret")).isFalse();
        assertThat(NoticeReceiptSignature.verify(body, signature, "wrong-secret")).isFalse();
    }
}
