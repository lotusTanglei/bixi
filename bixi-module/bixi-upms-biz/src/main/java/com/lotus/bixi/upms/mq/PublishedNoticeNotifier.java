package com.lotus.bixi.upms.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import com.lotus.bixi.upms.notification.NoticeChannelDispatcher;
import com.lotus.bixi.upms.notification.NoticeChannelRequest;
import com.lotus.bixi.upms.notification.NoticeChannelResult;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.mapper.SysNoticeMapper;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import com.lotus.bixi.upms.service.SysUserNoticeSseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Component
public class PublishedNoticeNotifier {

    private final SysNoticeMapper noticeMapper;
    private final SysUserNoticeMapper userNoticeMapper;
    private final SysUserNoticeSseService sseService;
    private final NoticeChannelDispatcher channelDispatcher;

    /** Spring constructor; ObjectProvider keeps focused in-app tests/provider-free deployments valid. */
    @Autowired
    public PublishedNoticeNotifier(SysNoticeMapper noticeMapper,
                                   SysUserNoticeMapper userNoticeMapper,
                                   SysUserNoticeSseService sseService,
                                   ObjectProvider<NoticeChannelDispatcher> dispatcherProvider) {
        this(noticeMapper, userNoticeMapper, sseService,
                dispatcherProvider == null ? null
                        : dispatcherProvider.getIfAvailable());
    }

    /** Backward-compatible constructor for the existing SSE-only test fixtures. */
    public PublishedNoticeNotifier(SysNoticeMapper noticeMapper,
                                   SysUserNoticeMapper userNoticeMapper,
                                   SysUserNoticeSseService sseService) {
        this(noticeMapper, userNoticeMapper, sseService, (NoticeChannelDispatcher) null);
    }

    public PublishedNoticeNotifier(SysNoticeMapper noticeMapper,
                                   SysUserNoticeMapper userNoticeMapper,
                                   SysUserNoticeSseService sseService,
                                   NoticeChannelDispatcher channelDispatcher) {
        this.noticeMapper = noticeMapper;
        this.userNoticeMapper = userNoticeMapper;
        this.sseService = sseService;
        this.channelDispatcher = channelDispatcher;
    }

    public void notifyRecipientsAfterCommit(Long noticeId) {
        Objects.requireNonNull(noticeId, "noticeId is required");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            notifyRecipients(noticeId);
            return;
        }
        Long tenantId = TenantContextHolder.get();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Long previousTenant = TenantContextHolder.get();
                try {
                    TenantContextHolder.set(tenantId);
                    notifyRecipients(noticeId);
                } catch (RuntimeException failure) {
                    log.warn("Notice was committed but its SSE refresh failed, noticeId={}", noticeId, failure);
                } finally {
                    if (previousTenant == null) TenantContextHolder.clear();
                    else TenantContextHolder.set(previousTenant);
                }
            }
        });
    }

    public void notifyRecipients(Long noticeId) {
        Objects.requireNonNull(noticeId, "noticeId is required");
        SysNotice notice = noticeMapper.selectById(noticeId);
        if (notice == null || !"1".equals(notice.getStatus())) {
            log.info("Skip notification for unavailable notice: {}", noticeId);
            return;
        }

        List<SysUserNotice> recipients = userNoticeMapper.selectList(
                Wrappers.<SysUserNotice>lambdaQuery()
                        .eq(SysUserNotice::getNoticeId, noticeId)
                        .apply("del_flag = '0'"));
        if (recipients == null || recipients.isEmpty()) {
            log.info("No receivers found for notice: {}", noticeId);
            return;
        }

        RuntimeException firstFailure = null;
        int delivered = 0;
        for (SysUserNotice recipient : recipients) {
            Long recipientId = recipient.getId();
            if (recipientId == null || SysUserNotice.DELIVERY_DELIVERED.equals(recipient.getDeliveryStatus())) {
                continue;
            }

            int previousAttempts = recipient.getDeliveryAttempts() == null
                    ? 0 : Math.max(0, recipient.getDeliveryAttempts());
            LocalDateTime staleBefore = LocalDateTime.now()
                    .minusSeconds(SysUserNotice.DELIVERY_LEASE_SECONDS);

            // A conditional update is the delivery fence. A zero result means
            // another consumer already owns this attempt or the row is done.
            int claimed = userNoticeMapper.claimDelivery(recipientId, staleBefore, previousAttempts);
            if (claimed == 0) {
                continue;
            }
            int expectedAttempt = previousAttempts + 1;
            try {
                NoticeChannel channel = NoticeChannel.parse(notice.getDeliveryChannel());
                if (channel == NoticeChannel.IN_APP) {
                    sseService.publishRefresh(recipient.getUserId(), recipient.getNoticeId(), recipientId);
                } else if (channelDispatcher == null) {
                    throw new IllegalStateException("notice channel dispatcher is not configured");
                } else {
                    NoticeChannelResult result = channelDispatcher.dispatch(
                            new NoticeChannelRequest(channel, TenantContextHolder.get(), notice.getId(),
                                    recipient.getUserId(), notice.getTitle(), notice.getContent())
                                    .withUserNoticeId(recipientId),
                            recipient.getUserId());
                    if (!result.delivered()) {
                        throw new IllegalStateException(result.code() + ": " + result.message());
                    }
                }
                if (userNoticeMapper.markDeliveryDelivered(recipientId, expectedAttempt) != 1) {
                    throw new IllegalStateException("notice delivery state could not be marked DELIVERED");
                }
                delivered++;
            } catch (RuntimeException failure) {
                String error = summarize(failure);
                try {
                    userNoticeMapper.markDeliveryFailed(recipientId, expectedAttempt, error);
                } catch (RuntimeException stateFailure) {
                    log.warn("Could not persist notice delivery failure, recipientId={}", recipientId,
                            stateFailure);
                }
                if (firstFailure == null) {
                    firstFailure = failure;
                }
                log.warn("Notice SSE refresh failed, recipientId={}", recipientId, failure);
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
        log.info("Notice notification sent to {} users via SSE, noticeId={}", delivered, noticeId);
    }

    private static String summarize(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        message = message.trim();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
