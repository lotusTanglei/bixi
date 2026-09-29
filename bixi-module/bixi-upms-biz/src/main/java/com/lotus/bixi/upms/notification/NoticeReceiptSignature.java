package com.lotus.bixi.upms.notification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** HMAC-SHA256 verifier for provider callbacks. */
public final class NoticeReceiptSignature {

    private NoticeReceiptSignature() {
    }

    public static boolean verify(String body, String supplied, String secret) {
        if (body == null || supplied == null || supplied.isBlank() || secret == null || secret.isBlank()) {
            return false;
        }
        String value = supplied.trim();
        if (value.regionMatches(true, 0, "sha256=", 0, 7)) {
            value = value.substring(7);
        }
        final byte[] expected;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException failure) {
            return false;
        }
        final byte[] actual;
        try {
            actual = HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException invalidHex) {
            return false;
        }
        return java.security.MessageDigest.isEqual(expected, actual);
    }

    public static String sign(String body, String secret) {
        if (body == null || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("body and secret are required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", failure);
        }
    }
}
