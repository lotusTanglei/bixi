package com.lotus.bixi.upms.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import com.lotus.bixi.upms.sse.UserNoticeSseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统通知消费者（UPMS 统一消费下发通知）
 *
 * 消费逻辑：
 * - 确保 sys_notice 存在且状态为发布
 * - 确保 sys_user_notice 存在（由 Service 层创建）
 * - 发送 SSE 通知
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

        SysNotice notice;
        if (noticeDTO.getNoticeId() != null) {
            notice = noticeService.getById(noticeDTO.getNoticeId());
            if (notice == null) {
                log.error("Notice not found: {}", noticeDTO.getNoticeId());
                notice = createNoticeFromDto(noticeDTO);
            } else {
                // Ensure status is Published
                if (!"1".equals(notice.getStatus())) {
                    notice.setStatus("1");
                    noticeService.updateById(notice);
                }
            }
        } else {
            notice = createNoticeFromDto(noticeDTO);
        }

        Long noticeId = notice.getId();

        // Fetch recipients from sys_user_notice
        // (They are created by SysNoticeService.save/update now)
        List<SysUserNotice> userNotices = userNoticeService.list(Wrappers.<SysUserNotice>lambdaQuery()
                .eq(SysUserNotice::getNoticeId, noticeId));

        if (userNotices == null || userNotices.isEmpty()) {
            log.info("No receivers found for notice: {}", noticeId);
            return;
        }

        for (SysUserNotice userNotice : userNotices) {
            userNoticeSseService.publishRefresh(userNotice.getUserId(), userNotice.getNoticeId(), userNotice.getId());
        }

        log.info("Notice notification sent to {} users via SSE, noticeId={}", userNotices.size(), noticeId);
    }


    private SysNotice createNoticeFromDto(NoticeMessageDTO noticeDTO) {
        SysNoticeVO noticeVO = new SysNoticeVO();
        noticeVO.setTitle(noticeDTO.getTitle());
        noticeVO.setContent(noticeDTO.getContent());
        noticeVO.setSenderId(noticeDTO.getSenderId());
        noticeVO.setType(noticeDTO.getType() == null ? "0" : noticeDTO.getType());
        noticeVO.setStatus("1");
        noticeVO.setTargetType(noticeDTO.getTargetType());
        noticeVO.setTargetIds(noticeDTO.getTargetIds());
        noticeService.saveNotice(noticeVO);
        return noticeVO;
    }

}
