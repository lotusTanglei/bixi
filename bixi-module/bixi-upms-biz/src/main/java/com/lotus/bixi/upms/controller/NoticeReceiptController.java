package com.lotus.bixi.upms.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.upms.notification.NoticeChannelProperties;
import com.lotus.bixi.upms.notification.NoticeDeliveryReceipt;
import com.lotus.bixi.upms.notification.NoticeReceiptResult;
import com.lotus.bixi.upms.notification.NoticeReceiptService;
import com.lotus.bixi.upms.notification.NoticeReceiptSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, HMAC-protected callback endpoint for asynchronous provider receipts. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/notice/delivery")
@Tag(name = "通知第三方回执")
public class NoticeReceiptController {

    private final ObjectMapper objectMapper;
    private final NoticeChannelProperties properties;
    private final NoticeReceiptService receiptService;

    @Operation(summary = "接收通知第三方回执", description = "接收签名校验后的 Email、短信、微信或 Webhook 厂商回执")
    @Inner(value = false)
    @PostMapping(value = "/receipt", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<R<NoticeReceiptResult>> receive(HttpServletRequest request,
                                                           @RequestBody String body) {
        NoticeChannelProperties.Receipt receiptProperties = properties.getReceipt();
        if (receiptProperties == null || !receiptProperties.isEnabled()
                || receiptProperties.getSecret() == null || receiptProperties.getSecret().isBlank()) {
            return response(HttpStatus.SERVICE_UNAVAILABLE,
                    NoticeReceiptResult.rejected("NOT_CONFIGURED", "notice receipt callback is not configured"));
        }
        if (body == null || body.isBlank() || body.length() > 1_000_000) {
            return response(HttpStatus.BAD_REQUEST,
                    NoticeReceiptResult.rejected("INVALID_BODY", "receipt body is required"));
        }
        String headerName = receiptProperties.getSignatureHeader();
        if (headerName == null || headerName.isBlank()) {
            headerName = "X-Bixi-Notice-Signature";
        }
        String signature = request == null ? null : request.getHeader(headerName);
        if (!NoticeReceiptSignature.verify(body, signature, receiptProperties.getSecret())) {
            return response(HttpStatus.UNAUTHORIZED,
                    NoticeReceiptResult.rejected("INVALID_SIGNATURE", "receipt signature is invalid"));
        }

        final NoticeDeliveryReceipt callback;
        try {
            callback = objectMapper.readValue(body, NoticeDeliveryReceipt.class);
        } catch (JsonProcessingException invalidJson) {
            return response(HttpStatus.BAD_REQUEST,
                    NoticeReceiptResult.rejected("INVALID_BODY", "receipt body is not valid JSON"));
        }
        Long previousTenant = TenantContextHolder.get();
        try {
            if (callback == null || callback.getTenantId() == null || callback.getTenantId() <= 0) {
                return response(HttpStatus.BAD_REQUEST,
                        NoticeReceiptResult.rejected("TENANT_REQUIRED", "receipt tenantId is required"));
            }
            TenantContextHolder.set(callback.getTenantId());
            NoticeReceiptResult result = receiptService.apply(callback);
            if (result.accepted()) {
                return response(HttpStatus.OK, result);
            }
            HttpStatus status = switch (result.code()) {
                case "RECIPIENT_NOT_FOUND", "NOTICE_MISMATCH" -> HttpStatus.NOT_FOUND;
                case "TENANT_MISMATCH" -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.BAD_REQUEST;
            };
            return response(status, result);
        } finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }

    private static ResponseEntity<R<NoticeReceiptResult>> response(HttpStatus status,
                                                                     NoticeReceiptResult result) {
        return ResponseEntity.status(status).body(result.accepted() ? R.ok(result) : R.failed(result));
    }
}
