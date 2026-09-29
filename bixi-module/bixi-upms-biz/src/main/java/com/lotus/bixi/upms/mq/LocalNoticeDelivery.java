package com.lotus.bixi.upms.mq;

import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "single")
public class LocalNoticeDelivery implements NoticeDelivery {

    private final PublishedNoticeNotifier notifier;

    @Override
    public void deliver(NoticeMessageDTO message) {
        Objects.requireNonNull(message, "message is required");
        notifier.notifyRecipients(Objects.requireNonNull(message.getNoticeId(), "noticeId is required"));
    }
}
