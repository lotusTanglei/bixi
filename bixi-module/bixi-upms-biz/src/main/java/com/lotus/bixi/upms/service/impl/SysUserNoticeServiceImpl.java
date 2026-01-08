package com.lotus.bixi.upms.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.vo.UserNoticeVO;
import com.lotus.bixi.upms.mapper.SysUserNoticeMapper;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/**
 * 用户消息关联表 服务实现类
 *
 * @author bixi
 * @date 2025-01-01
 */
@Service
public class SysUserNoticeServiceImpl extends ServiceImpl<SysUserNoticeMapper, SysUserNotice> implements SysUserNoticeService {

    @Override
    public IPage<UserNoticeVO> getUserNoticePage(Page page, UserNoticeVO userNoticeVO) {
        return baseMapper.selectUserNoticePage(page, userNoticeVO);
    }

    @Override
    public UserNoticeVO getUserNoticeById(Long id) {
        return baseMapper.selectUserNoticeById(id);
    }

    @Override
    public boolean markRead(Long userNoticeId, Long userId) {
        return this.update(Wrappers.<SysUserNotice>lambdaUpdate()
                .set(SysUserNotice::getIsRead, "1")
                .set(SysUserNotice::getReadTime, LocalDateTime.now())
                .eq(SysUserNotice::getId, userNoticeId)
                .eq(SysUserNotice::getUserId, userId));
    }

    @Override
    public int markAllRead(Long userId) {
        return this.baseMapper.update(null, Wrappers.<SysUserNotice>lambdaUpdate()
                .set(SysUserNotice::getIsRead, "1")
                .set(SysUserNotice::getReadTime, LocalDateTime.now())
                .eq(SysUserNotice::getUserId, userId)
                .eq(SysUserNotice::getIsRead, "0"));
    }

    @Override
    public int deleteAll(Long userId) {
        return this.baseMapper.delete(Wrappers.<SysUserNotice>lambdaQuery()
                .eq(SysUserNotice::getUserId, userId));
    }

    @Override
    public boolean deleteOne(Long userNoticeId, Long userId) {
        return this.remove(Wrappers.<SysUserNotice>lambdaQuery()
                .eq(SysUserNotice::getId, userNoticeId)
                .eq(SysUserNotice::getUserId, userId));
    }
}
