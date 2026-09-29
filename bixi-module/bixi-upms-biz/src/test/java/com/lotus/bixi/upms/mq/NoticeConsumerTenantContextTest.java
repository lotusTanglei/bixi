package com.lotus.bixi.upms.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.exception.TenantNotSetException;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.service.SysNoticeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class NoticeConsumerTenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
    }

    @Test
    void appliesMessageTenantWhileProcessingAndRestoresTheCallerContext() throws Exception {
        SysNoticeService notices = mock(SysNoticeService.class);
        PublishedNoticeNotifier notifier = mock(PublishedNoticeNotifier.class);
        NoticeConsumer consumer = new NoticeConsumer(notices, notifier);
        NoticeMessageDTO message = new ObjectMapper().readValue(
                "{\"noticeId\":99,\"tenantId\":42}", NoticeMessageDTO.class);
        AtomicReference<Long> observedTenant = new AtomicReference<>();
        doAnswer(invocation -> {
            observedTenant.set(TenantContextHolder.get());
            return null;
        }).when(notifier).notifyRecipients(eq(99L));

        TenantContextHolder.set(7L);
        consumer.handleNoticeMessage(message);

        assertThat(observedTenant).hasValue(42L);
        assertThat(TenantContextHolder.get()).isEqualTo(7L);
    }

    @Test
    void rejectsMessagesWithoutAnExplicitTenant() {
        SysNoticeService notices = mock(SysNoticeService.class);
        PublishedNoticeNotifier notifier = mock(PublishedNoticeNotifier.class);
        NoticeConsumer consumer = new NoticeConsumer(notices, notifier);
        NoticeMessageDTO message = new NoticeMessageDTO();
        message.setNoticeId(99L);

        TenantContextHolder.set(7L);
        assertThatThrownBy(() -> consumer.handleNoticeMessage(message))
                .isInstanceOf(TenantNotSetException.class)
                .hasMessageContaining("Tenant ID");
        verifyNoInteractions(notifier);
        assertThat(TenantContextHolder.get()).isEqualTo(7L);
    }
}
