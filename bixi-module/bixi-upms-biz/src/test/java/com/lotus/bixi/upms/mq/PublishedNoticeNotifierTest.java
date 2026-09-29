package com.lotus.bixi.upms.mq;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.mapper.SysNoticeMapper;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import com.lotus.bixi.upms.notification.NoticeChannelDispatcher;
import com.lotus.bixi.upms.notification.NoticeChannelResult;
import com.lotus.bixi.upms.service.SysUserNoticeSseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class PublishedNoticeNotifierTest {

    @AfterEach
    void cleanupSynchronization() {
        TenantContextHolder.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void defersRefreshUntilTheNoticeTransactionHasCommitted() {
        SysNoticeMapper notices = mock(SysNoticeMapper.class);
        SysUserNoticeMapper recipients = mock(SysUserNoticeMapper.class);
        SysUserNoticeSseService sse = mock(SysUserNoticeSseService.class);
        PublishedNoticeNotifier notifier = new PublishedNoticeNotifier(notices, recipients, sse);
        SysNotice notice = mock(SysNotice.class);
        SysUserNotice recipient = mock(SysUserNotice.class);
        when(notice.getStatus()).thenReturn("1");
        when(notices.selectById(99L)).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.get()).isEqualTo(42L);
            return notice;
        });
        when(recipients.selectList(any())).thenReturn(List.of(recipient));
        when(recipient.getUserId()).thenReturn(22L);
        when(recipient.getNoticeId()).thenReturn(99L);
        when(recipient.getId()).thenReturn(100L);
        when(recipient.getDeliveryAttempts()).thenReturn(0);
        when(recipients.claimDelivery(eq(100L), any(LocalDateTime.class), eq(0))).thenReturn(1);
        when(recipients.markDeliveryDelivered(100L, 1)).thenReturn(1);
        TenantContextHolder.set(42L);
        TransactionSynchronizationManager.initSynchronization();

        notifier.notifyRecipientsAfterCommit(99L);

        verifyNoInteractions(notices, recipients, sse);
        TenantContextHolder.clear();
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(sse).publishRefresh(22L, 99L, 100L);
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void externalChannelUsesTheSameFencedRecipientStatus() {
        SysNoticeMapper notices = mock(SysNoticeMapper.class);
        SysUserNoticeMapper recipients = mock(SysUserNoticeMapper.class);
        SysUserNoticeSseService sse = mock(SysUserNoticeSseService.class);
        NoticeChannelDispatcher dispatcher = mock(NoticeChannelDispatcher.class);
        PublishedNoticeNotifier notifier = new PublishedNoticeNotifier(notices, recipients, sse, dispatcher);
        SysNotice notice = mock(SysNotice.class);
        SysUserNotice recipient = mock(SysUserNotice.class);
        when(notice.getStatus()).thenReturn("1");
        when(notice.getDeliveryChannel()).thenReturn(NoticeChannel.EMAIL.name());
        when(notice.getId()).thenReturn(99L);
        when(notice.getTitle()).thenReturn("title");
        when(notice.getContent()).thenReturn("content");
        when(notices.selectById(99L)).thenReturn(notice);
        when(recipients.selectList(any())).thenReturn(List.of(recipient));
        when(recipient.getId()).thenReturn(100L);
        when(recipient.getUserId()).thenReturn(22L);
        when(recipient.getNoticeId()).thenReturn(99L);
        when(recipient.getDeliveryAttempts()).thenReturn(0);
        when(recipients.claimDelivery(eq(100L), any(LocalDateTime.class), eq(0))).thenReturn(1);
        when(recipients.markDeliveryDelivered(100L, 1)).thenReturn(1);
        when(dispatcher.dispatch(any(), eq(22L))).thenReturn(NoticeChannelResult.success());
        TenantContextHolder.set(42L);

        notifier.notifyRecipients(99L);

        verify(dispatcher).dispatch(any(), eq(22L));
        verify(recipients).markDeliveryDelivered(100L, 1);
        verifyNoInteractions(sse);
    }
}
