package com.lotus.bixi.upms.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeSseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统通知消费者（UPMS 统一消费下发通知）
 *
 * 核心职责：
 * - 消费 MQ 中的通知消息
 * - 保证通知状态为已发布
 * - 基于 sys_user_notice 下发 SSE 通知
 *
 * 处理流程：
 * - noticeId 存在时，直接加载通知并确保状态
 * - noticeId 不存在时，根据 DTO 创建通知
 * - 读取接收人列表并逐个推送 SSE
 *
 * @author bixi
 */
@Slf4j
@Component
@AllArgsConstructor
public class NoticeConsumer {

    private final SysNoticeService noticeService;
    private final SysUserNoticeService userNoticeService;
    private final SysUserNoticeSseService sysUserNoticeSseService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "", durable = "false", autoDelete = "true"),
            exchange = @Exchange(value = MQConstants.SYS_NOTICE_FANOUT_EXCHANGE, type = "fanout", durable = "true")
    ))
    public void handleNoticeMessage(NoticeMessageDTO noticeDTO) {
        if (noticeDTO == null) {
            return;
        }

        // 根据通知ID获取通知，或根据消息内容新建通知
        SysNotice notice;
        if (noticeDTO.getNoticeId() != null) {
            notice = noticeService.getById(noticeDTO.getNoticeId());
            if (notice == null) {
                log.error("Notice not found: {}", noticeDTO.getNoticeId());
                notice = createNoticeFromDto(noticeDTO);
            } else {
                // 确保通知状态为已发布
                if (!"1".equals(notice.getStatus())) {
                    notice.setStatus("1");
                    noticeService.updateById(notice);
                }
            }
        } else {
            notice = createNoticeFromDto(noticeDTO);
        }

        // 基于通知ID获取接收人列表
        Long noticeId = notice.getId();

        // 从 sys_user_notice 表读取接收人（由保存/更新通知时创建）
        List<SysUserNotice> userNotices = userNoticeService.list(Wrappers.<SysUserNotice>lambdaQuery()
                .eq(SysUserNotice::getNoticeId, noticeId));

        if (userNotices == null || userNotices.isEmpty()) {
            log.info("No receivers found for notice: {}", noticeId);
            return;
        }

        // 逐个推送 SSE 刷新事件
        for (SysUserNotice userNotice : userNotices) {
            sysUserNoticeSseService.publishRefresh(userNotice.getUserId(), userNotice.getNoticeId(), userNotice.getId());
        }

        log.info("Notice notification sent to {} users via SSE, noticeId={}", userNotices.size(), noticeId);
    }


    private SysNotice createNoticeFromDto(NoticeMessageDTO noticeDTO) {
        // 根据消息内容构建通知并落库
        SysNoticeVO noticeVO = new SysNoticeVO();
        noticeVO.setTitle(noticeDTO.getTitle());
        noticeVO.setContent(noticeDTO.getContent());
        noticeVO.setSenderId(noticeDTO.getSenderId());
        noticeVO.setType(noticeDTO.getType() == null ? "0" : noticeDTO.getType());
        noticeVO.setStatus("1");
        noticeVO.setTargetType(noticeDTO.getTargetType());
        noticeVO.setTargetIds(noticeDTO.getTargetIds());
        // 保存通知并生成接收人关联记录
        noticeService.saveNotice(noticeVO);
        return noticeVO;
    }

}
