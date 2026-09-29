package com.lotus.bixi.upms.mq;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.exception.TenantNotSetException;
import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.constant.NoticeChannel;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.service.SysNoticeService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 系统通知消费者（UPMS 统一消费下发通知）
 *
 * 核心职责：
 * - 消费 MQ 中的通知消息
 * - 只转发已发布通知，或从可信系统消息创建已发布通知
 * - 基于 sys_user_notice 下发 SSE 通知
 *
 * 处理流程：
 * - noticeId 存在时，只读取当前已发布通知
 * - noticeId 不存在时，根据 DTO 创建通知
 * - 读取接收人列表并逐个推送 SSE
 *
 * @author bixi
 */
@Slf4j
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "cloud", matchIfMissing = true)
public class NoticeConsumer {

    private final SysNoticeService noticeService;
    private final PublishedNoticeNotifier notifier;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "", durable = "false", autoDelete = "true"),
            exchange = @Exchange(value = MQConstants.SYS_NOTICE_FANOUT_EXCHANGE, type = "fanout", durable = "true")
    ))
    public void handleNoticeMessage(NoticeMessageDTO noticeDTO) {
        if (noticeDTO == null) {
            return;
        }

        Long tenantId = noticeDTO.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new TenantNotSetException();
        }
        Long previousTenant = TenantContextHolder.get();
        try {
            TenantContextHolder.set(tenantId);
            Long noticeId = noticeDTO.getNoticeId() != null
                    ? noticeDTO.getNoticeId()
                    : createNoticeFromDto(noticeDTO).getId();
            notifier.notifyRecipients(noticeId);
        }
        finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }


    private SysNotice createNoticeFromDto(NoticeMessageDTO noticeDTO) {
        // 根据消息内容构建通知并落库
        SysNoticeVO noticeVO = new SysNoticeVO();
        noticeVO.setTitle(noticeDTO.getTitle());
        noticeVO.setContent(noticeDTO.getContent());
        noticeVO.setSenderId(noticeDTO.getSenderId());
        noticeVO.setType(noticeDTO.getType() == null ? "0" : noticeDTO.getType());
        noticeVO.setDeliveryChannel(NoticeChannel.parse(noticeDTO.getDeliveryChannel()).name());
        noticeVO.setTargetType(noticeDTO.getTargetType());
        noticeVO.setTargetIds(noticeDTO.getTargetIds());
        // 保存通知并生成接收人关联记录
        if (!noticeService.savePublishedNotice(noticeVO)) {
            throw new IllegalStateException("系统通知保存失败");
        }
        return noticeVO;
    }

}
