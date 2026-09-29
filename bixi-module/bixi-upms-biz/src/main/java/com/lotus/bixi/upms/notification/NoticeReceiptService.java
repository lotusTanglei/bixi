package com.lotus.bixi.upms.notification;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/**
 * Applies signed provider receipts to one recipient row. The mapper update is
 * a monotonic compare-and-set: a late failure cannot overwrite a delivered
 * receipt, and replaying the same callback is harmless.
 */
@Service
@RequiredArgsConstructor
public class NoticeReceiptService {

    private final SysUserNoticeMapper userNoticeMapper;

    @Transactional
    public NoticeReceiptResult apply(NoticeDeliveryReceipt receipt) {
        if (receipt == null || receipt.getTenantId() == null || receipt.getTenantId() <= 0) {
            return NoticeReceiptResult.rejected("TENANT_REQUIRED", "receipt tenantId is required");
        }
        Long currentTenant = TenantContextHolder.get();
        if (!Objects.equals(currentTenant, receipt.getTenantId())) {
            return NoticeReceiptResult.rejected("TENANT_MISMATCH", "receipt tenant does not match context");
        }
        String status;
        try {
            status = normalizeStatus(receipt.getStatus());
        } catch (IllegalArgumentException invalid) {
            return NoticeReceiptResult.rejected("INVALID_STATUS", invalid.getMessage());
        }

        SysUserNotice row = findRecipient(receipt);
        if (row == null || row.getId() == null) {
            return NoticeReceiptResult.rejected("RECIPIENT_NOT_FOUND", "notice recipient is unavailable");
        }
        if (receipt.getNoticeId() != null && !Objects.equals(receipt.getNoticeId(), row.getNoticeId())) {
            return NoticeReceiptResult.rejected("NOTICE_MISMATCH", "receipt notice does not match recipient");
        }

        String safeCode = sanitizeCode(receipt.getCode());
        int changed = userNoticeMapper.applyDeliveryReceipt(
                row.getId(), row.getNoticeId(), status, safeCode);
        return changed == 1
                ? NoticeReceiptResult.updated(status, safeCode)
                : NoticeReceiptResult.duplicate(status, safeCode);
    }

    private SysUserNotice findRecipient(NoticeDeliveryReceipt receipt) {
        if (receipt.getUserNoticeId() != null) {
            return userNoticeMapper.selectById(receipt.getUserNoticeId());
        }
        if (receipt.getNoticeId() == null || receipt.getRecipientId() == null) {
            return null;
        }
        return userNoticeMapper.selectDeliveryByNoticeAndUser(receipt.getNoticeId(), receipt.getRecipientId());
    }

    static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("receipt status is required");
        }
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DELIVERED", "SUCCESS", "SUCCEEDED", "OK" -> SysUserNotice.RECEIPT_DELIVERED;
            case "FAILED", "FAILURE", "ERROR", "BOUNCED", "REJECTED" -> SysUserNotice.RECEIPT_FAILED;
            case "PENDING", "QUEUED", "ACCEPTED", "SENT", "PROCESSING" -> SysUserNotice.RECEIPT_PENDING;
            default -> throw new IllegalArgumentException("unsupported receipt status: " + raw);
        };
    }

    /** Keep vendor codes useful for operators without retaining arbitrary PII. */
    static String sanitizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim();
        return code.length() <= 64 && code.matches("[A-Za-z0-9._:-]+") ? code : "REDACTED";
    }
}
