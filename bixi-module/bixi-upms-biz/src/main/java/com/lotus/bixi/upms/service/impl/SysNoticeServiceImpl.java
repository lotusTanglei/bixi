package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.constant.MQConstants;
import com.lotus.bixi.upms.api.dto.NoticeMessageDTO;
import com.lotus.bixi.upms.api.entity.SysNotice;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.entity.SysUserNotice;
import com.lotus.bixi.upms.api.entity.SysUserRole;
import com.lotus.bixi.upms.api.vo.SysNoticeVO;
import com.lotus.bixi.upms.mapper.SysNoticeMapper;
import com.lotus.bixi.upms.service.SysNoticeService;
import com.lotus.bixi.upms.service.SysUserNoticeService;
import com.lotus.bixi.upms.service.SysUserRoleService;
import com.lotus.bixi.upms.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 消息通知表 服务实现类
 *
 * @author bixi
 * @date 2024-05-20
 */
@Service
@RequiredArgsConstructor
public class SysNoticeServiceImpl extends ServiceImpl<SysNoticeMapper, SysNotice> implements SysNoticeService {

    private final RabbitTemplate rabbitTemplate;
    private final SysUserService userService;
    private final SysUserRoleService userRoleService;
    private final SysUserNoticeService userNoticeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveNotice(SysNoticeVO vo) {
        boolean result = super.save(vo);
        if (result && StrUtil.isNotBlank(vo.getTargetType())) {
            resolveAndSaveRecipients(vo.getId(), vo.getTargetType(), vo.getTargetIds());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateNotice(SysNoticeVO vo) {
        boolean result = super.updateById(vo);
        if (result && StrUtil.isNotBlank(vo.getTargetType())) {
            resolveAndSaveRecipients(vo.getId(), vo.getTargetType(), vo.getTargetIds());
        }
        return result;
    }

    private void resolveAndSaveRecipients(Long noticeId, String targetType, String targetIds) {
        // 1. Delete existing recipients
        userNoticeService.remove(Wrappers.<SysUserNotice>lambdaQuery().eq(SysUserNotice::getNoticeId, noticeId));

        Set<Long> receiverIds = new HashSet<>();

        if ("0".equals(targetType)) { // All Users
            List<SysUser> users = userService.list(Wrappers.<SysUser>lambdaQuery().select(SysUser::getId));
            if (users != null) {
                receiverIds.addAll(users.stream().map(SysUser::getId).collect(Collectors.toList()));
            }
        } else if (StrUtil.isNotBlank(targetIds)) {
            List<String> ids = Arrays.asList(targetIds.split(","));
            if ("1".equals(targetType)) { // Department
                List<SysUser> users = userService.list(Wrappers.<SysUser>lambdaQuery()
                        .select(SysUser::getId)
                        .in(SysUser::getDeptId, ids));
                if (users != null) {
                    receiverIds.addAll(users.stream().map(SysUser::getId).collect(Collectors.toList()));
                }
            } else if ("2".equals(targetType)) { // Role
                List<SysUserRole> userRoles = userRoleService.list(Wrappers.<SysUserRole>lambdaQuery()
                        .select(SysUserRole::getUserId)
                        .in(SysUserRole::getRoleId, ids));
                if (userRoles != null) {
                    receiverIds.addAll(userRoles.stream().map(SysUserRole::getUserId).collect(Collectors.toList()));
                }
            } else if ("3".equals(targetType)) { // Specific Users
                receiverIds.addAll(ids.stream().map(Long::valueOf).collect(Collectors.toList()));
            }
        }

        if (!receiverIds.isEmpty()) {
            List<SysUserNotice> userNotices = new ArrayList<>(receiverIds.size());
            for (Long userId : receiverIds) {
                if (userId == null) continue;
                SysUserNotice userNotice = new SysUserNotice();
                userNotice.setNoticeId(noticeId);
                userNotice.setUserId(userId);
                userNotice.setIsRead("0");
                userNotices.add(userNotice);
            }
            userNoticeService.saveBatch(userNotices);
        }
    }

    @Override
    public boolean sendNotice(Long id) {
        SysNotice notice = this.getById(id);
        if (notice == null) {
            return false;
        }

        // Update status to Published (1)
        notice.setStatus("1");
        this.updateById(notice);

        // Build DTO
        NoticeMessageDTO dto = new NoticeMessageDTO();
        dto.setNoticeId(notice.getId());
        dto.setTitle(notice.getTitle());
        dto.setContent(notice.getContent());
        dto.setType(notice.getType());
        dto.setSenderId(notice.getSenderId());
        // Target info is not stored in DB anymore, and resolution is done.
        // Consumer just needs to notify.

        rabbitTemplate.convertAndSend(MQConstants.SYS_NOTICE_QUEUE, dto);
        return true;
    }
}
