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
     * 发送通知
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

    /**
     * 更新通知（包含目标用户解析）
     * @param vo 通知VO
     * @return boolean
     */
    boolean updateNotice(SysNoticeVO vo);
}
