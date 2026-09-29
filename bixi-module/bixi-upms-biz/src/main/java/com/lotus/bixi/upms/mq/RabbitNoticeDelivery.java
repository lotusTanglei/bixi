package com.lotus.bixi.upms.mq;

import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "cloud", matchIfMissing = true)
public class RabbitNoticeDelivery implements NoticeDelivery {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void deliver(NoticeMessageDTO message) {
        rabbitTemplate.convertAndSend(MQConstants.SYS_NOTICE_FANOUT_EXCHANGE, "", message);
    }
}
