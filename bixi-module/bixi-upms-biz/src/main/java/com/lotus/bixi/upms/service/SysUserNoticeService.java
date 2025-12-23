package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysUserNotice;

/**
 * 用户消息关联表 服务类
 *
 * @author bixi
 * @date 2024-05-20
 */
public interface SysUserNoticeService extends IService<SysUserNotice> {

    boolean markRead(Long userNoticeId, Long userId);

    int markAllRead(Long userId);

    int deleteAll(Long userId);

    boolean deleteOne(Long userNoticeId, Long userId);
}
