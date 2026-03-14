package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.vo.SessionVO;
import com.lotus.bixi.ai.mapper.AiSessionMapper;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.common.security.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SessionServiceImpl extends ServiceImpl<AiSessionMapper, AiSession> implements SessionService {

    @Override
    public List<SessionVO> listSessions() {
        Long userId = SecurityUtils.getUser().getId();
        List<AiSession> sessions = lambdaQuery()
                .eq(AiSession::getUserId, userId)
                .eq(AiSession::getDelFlag, "0")
                .orderByDesc(AiSession::getUpdateTime)
                .list();
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
        save(session);
        return convertToVO(session);
    }

    @Override
    public SessionVO updateSession(SessionDTO dto) {
        AiSession session = getById(dto.getId());
        if (session != null) {
            session.setTitle(dto.getTitle());
            if (dto.getModel() != null) {
                session.setModel(dto.getModel());
            }
            updateById(session);
        }
        return convertToVO(session);
    }

    @Override
    public void deleteSession(Long id) {
        AiSession session = getById(id);
        if (session != null) {
            session.setDelFlag("1");
            updateById(session);
        }
    }

    @Override
    public SessionVO getSession(Long id) {
        AiSession session = getById(id);
        return session != null ? convertToVO(session) : null;
    }

    private SessionVO convertToVO(AiSession session) {
        SessionVO vo = new SessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }
}
