package com.lotus.bixi.upms.mq;

import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import com.lotus.bixi.upms.sse.UserNoticeSseService;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 系统通知消费者（UPMS 统一消费下发通知）
 *
 * 消费逻辑：
 * - 新增 sys_notice
 * - 批量新增 sys_user_notice（写入接收人未读状态）
 *
 * @author bixi
 */
@Slf4j
@Component
@AllArgsConstructor
public class NoticeConsumer {

    private final SysNoticeService noticeService;
    private final SysUserNoticeService userNoticeService;
    private final UserNoticeSseService userNoticeSseService;

    @RabbitListener(queuesToDeclare = @Queue(value = MQConstants.SYS_NOTICE_QUEUE, durable = "true"))
    public void handleNoticeMessage(NoticeMessageDTO noticeDTO) {
        if (noticeDTO == null) {
            return;
        }

        SysNotice notice = new SysNotice();
        notice.setTitle(noticeDTO.getTitle());
        notice.setContent(noticeDTO.getContent());
        notice.setSenderId(noticeDTO.getSenderId());
        notice.setType(noticeDTO.getType() == null ? "0" : noticeDTO.getType());
        notice.setStatus("1");
        noticeService.save(notice);

        if (noticeDTO.getReceiverIds() == null || noticeDTO.getReceiverIds().isEmpty()) {
            log.info("通知已保存，noticeId={}", notice.getId());
            return;
        }

        Long noticeId = notice.getId();
        List<SysUserNotice> userNotices = new ArrayList<>(noticeDTO.getReceiverIds().size());
        for (Long receiverId : noticeDTO.getReceiverIds()) {
            if (receiverId == null) {
                continue;
            }
            SysUserNotice userNotice = new SysUserNotice();
            userNotice.setNoticeId(noticeId);
            userNotice.setUserId(receiverId);
            userNotice.setIsRead("0");
            userNotices.add(userNotice);
        }

        if (!userNotices.isEmpty()) {
            userNoticeService.saveBatch(userNotices);
            for (SysUserNotice userNotice : userNotices) {
                userNoticeSseService.publishRefresh(userNotice.getUserId(), userNotice.getNoticeId(), userNotice.getId());
            }
        }

        log.info("通知已下发，noticeId={}, receivers={}", noticeId, userNotices.size());
    }

}
