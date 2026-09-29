package com.lotus.bixi.upms.notification;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeReceiptServiceTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void appliesSignedProviderResultAndOnlyPersistsSafeCode() {
        TenantContextHolder.set(7L);
        SysUserNoticeMapper mapper = mock(SysUserNoticeMapper.class);
        SysUserNotice row = row(88L, 99L, 22L);
        when(mapper.selectById(88L)).thenReturn(row);
        when(mapper.applyDeliveryReceipt(88L, 99L, SysUserNotice.RECEIPT_FAILED, "REDACTED"))
                .thenReturn(1);

        NoticeDeliveryReceipt receipt = receipt(7L, 99L, 88L, 22L, "failed", "phone=13800138000");
        NoticeReceiptResult result = new NoticeReceiptService(mapper).apply(receipt);

        assertThat(result.accepted()).isTrue();
        assertThat(result.updated()).isTrue();
        assertThat(result.receiptStatus()).isEqualTo(SysUserNotice.RECEIPT_FAILED);
        assertThat(result.code()).isEqualTo("REDACTED");
        verify(mapper).applyDeliveryReceipt(88L, 99L, SysUserNotice.RECEIPT_FAILED, "REDACTED");
    }

    @Test
    void lateFailureAfterDeliveredIsIdempotentAndUnknownStatusIsRejected() {
        TenantContextHolder.set(7L);
        SysUserNoticeMapper mapper = mock(SysUserNoticeMapper.class);
        SysUserNotice row = row(88L, 99L, 22L);
        when(mapper.selectById(88L)).thenReturn(row);
        when(mapper.applyDeliveryReceipt(88L, 99L, SysUserNotice.RECEIPT_FAILED, "E_TEMP"))
                .thenReturn(0);
        NoticeReceiptService service = new NoticeReceiptService(mapper);

        NoticeReceiptResult duplicate = service.apply(receipt(7L, 99L, 88L, 22L, "failed", "E_TEMP"));
        NoticeReceiptResult invalid = service.apply(receipt(7L, 99L, 88L, 22L, "mystery", "E_TEMP"));

        assertThat(duplicate.accepted()).isTrue();
        assertThat(duplicate.updated()).isFalse();
        assertThat(invalid.accepted()).isFalse();
        assertThat(invalid.code()).isEqualTo("INVALID_STATUS");
    }

    @Test
    void callbackCanResolveByNoticeAndUserForLegacyProviderPayload() {
        TenantContextHolder.set(7L);
        SysUserNoticeMapper mapper = mock(SysUserNoticeMapper.class);
        SysUserNotice row = row(88L, 99L, 22L);
        when(mapper.selectDeliveryByNoticeAndUser(99L, 22L)).thenReturn(row);
        when(mapper.applyDeliveryReceipt(eq(88L), eq(99L), eq(SysUserNotice.RECEIPT_DELIVERED), eq("OK")))
                .thenReturn(1);

        NoticeDeliveryReceipt receipt = receipt(7L, 99L, null, 22L, "success", "OK");
        NoticeReceiptResult result = new NoticeReceiptService(mapper).apply(receipt);

        assertThat(result.updated()).isTrue();
        verify(mapper).selectDeliveryByNoticeAndUser(99L, 22L);
    }

    private static SysUserNotice row(Long id, Long noticeId, Long userId) {
        SysUserNotice row = new SysUserNotice();
        row.setId(id);
        row.setNoticeId(noticeId);
        row.setUserId(userId);
        return row;
    }

    private static NoticeDeliveryReceipt receipt(Long tenantId, Long noticeId, Long userNoticeId,
                                                 Long recipientId, String status, String code) {
        NoticeDeliveryReceipt receipt = new NoticeDeliveryReceipt();
        receipt.setTenantId(tenantId);
        receipt.setNoticeId(noticeId);
        receipt.setUserNoticeId(userNoticeId);
        receipt.setRecipientId(recipientId);
        receipt.setStatus(status);
        receipt.setCode(code);
        return receipt;
    }
}
