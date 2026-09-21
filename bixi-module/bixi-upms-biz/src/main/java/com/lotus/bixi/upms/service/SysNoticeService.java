package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;

/**
 * 消息通知表 服务类
 *
 * @author bixi
 * @date 2025-01-01
 */
public interface SysNoticeService extends IService<SysNotice> {

    /**
     * 发布草稿或重发已发布通知的实时提醒
     * @param id 通知ID
     * @return boolean
     */
    boolean sendNotice(Long id);

    /**
     * 保存通知（包含目标用户解析）
     * @param vo 通知VO
     * @return boolean
     */
    boolean saveNotice(SysNoticeVO vo);

    /** 仅供可信系统消息消费者创建已发布通知；不再投递 MQ，避免消息循环。 */
    boolean savePublishedNotice(SysNoticeVO vo);

    /**
     * 更新通知（包含目标用户解析）
     * @param vo 通知VO
     * @return boolean
     */
    boolean updateNotice(SysNoticeVO vo);
}
