package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;

/**
 * 用户消息关联表 服务类
 *
 * @author bixi
 * @date 2025-01-01
 */
public interface SysUserNoticeService extends IService<SysUserNotice> {

    IPage<UserNoticeVO> getUserNoticePage(Page page, UserNoticeVO userNoticeVO);

    UserNoticeVO getUserNoticeById(Long id);

    boolean markRead(Long userNoticeId, Long userId);

    int markAllRead(Long userId);

    int deleteAll(Long userId);

    boolean deleteOne(Long userNoticeId, Long userId);
}
