package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.vo.SessionVO;
import com.lotus.bixi.ai.mapper.AiSessionMapper;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final AiSessionMapper sessionMapper;

    @Override
    public List<SessionVO> listSessions() {
        Long userId = SecurityUtils.getUser().getId();
        LambdaQueryWrapper<AiSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiSession::getUserId, userId)
                .eq(AiSession::getDelFlag, "0")
                .orderByDesc(AiSession::getUpdateTime);
        List<AiSession> sessions = sessionMapper.selectList(wrapper);
        return sessions.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public SessionVO createSession(SessionDTO dto) {
        Long userId = SecurityUtils.getUser().getId();
        AiSession session = new AiSession();
        session.setTitle(dto.getTitle());
        session.setUserId(userId);
        session.setModel(dto.getModel() != null ? dto.getModel() : "qwen-plus");
        session.setStatus("active");
        sessionMapper.insert(session);
        return convertToVO(session);
    }

    @Override
    public SessionVO updateSession(SessionDTO dto) {
        AiSession session = sessionMapper.selectById(dto.getId());
        if (session != null) {
            session.setTitle(dto.getTitle());
            if (dto.getModel() != null) {
                session.setModel(dto.getModel());
            }
            session.setUpdateTime(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
        return convertToVO(session);
    }

    @Override
    public void deleteSession(Long id) {
        AiSession session = sessionMapper.selectById(id);
        if (session != null) {
            session.setDelFlag("1");
            sessionMapper.updateById(session);
        }
    }

    @Override
    public SessionVO getSession(Long id) {
        AiSession session = sessionMapper.selectById(id);
        return session != null ? convertToVO(session) : null;
    }

    private SessionVO convertToVO(AiSession session) {
        SessionVO vo = new SessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }
}
