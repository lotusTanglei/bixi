package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.api.vo.SessionVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.ai.mapper.AiSessionMapper;
import com.lotus.bixi.ai.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnAiEnabled
public class SessionServiceImpl extends ServiceImpl<AiSessionMapper, AiSession> implements SessionService {

    private final AiMessageMapper messageMapper;
    private final AiConversationMapper conversationMapper;

    @Override
    public List<SessionVO> listSessions() {
        var user = AiOwnershipSupport.requireUser();
        List<AiSession> sessions = lambdaQuery()
                .eq(AiSession::getUserId, user.getId())
                .eq(AiSession::getTenantId, AiOwnershipSupport.tenantId())
                .eq(AiSession::getDelFlag, "0")
                .orderByDesc(AiSession::getUpdateTime)
                .list();
        return sessions.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public SessionVO createSession(SessionDTO dto) {
        AiOwnershipSupport.requireWritable();
        var user = AiOwnershipSupport.requireUser();
        AiSession session = new AiSession();
        session.setTitle(dto.getTitle());
        session.setUserId(user.getId());
        session.setTenantId(AiOwnershipSupport.tenantId());
        session.setModel(dto.getModel() != null ? dto.getModel() : "qwen-plus");
        session.setStatus("active");
        if (!save(session)) {
            throw new AiException("AI会话保存失败");
        }
        return convertToVO(session);
    }

    @Override
    public SessionVO updateSession(SessionDTO dto) {
        AiOwnershipSupport.requireWritable();
        AiSession session = requireOwnedSession(dto == null ? null : dto.getId());
        session.setTitle(dto.getTitle());
        if (dto.getModel() != null) {
            session.setModel(dto.getModel());
        }
        if (!updateById(session)) {
            throw new AiException("AI会话更新失败");
        }
        return convertToVO(session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        AiOwnershipSupport.requireWritable();
        AiSession session = requireOwnedSession(id);
        deleteHistory(session);
        if (!removeById(id)) {
            throw AiOwnershipSupport.missing("AI会话");
        }
    }

    @Override
    public SessionVO getSession(Long id) {
        return convertToVO(requireOwnedSession(id));
    }

    @Override
    public AiSession requireOwnedSession(Long id) {
        if (id == null) {
            throw AiOwnershipSupport.missing("AI会话");
        }
        var user = AiOwnershipSupport.requireUser();
        AiSession session = lambdaQuery()
                .eq(AiSession::getId, id)
                .eq(AiSession::getUserId, user.getId())
                .eq(AiSession::getTenantId, AiOwnershipSupport.tenantId())
                .eq(AiSession::getDelFlag, "0")
                .one();
        if (session == null) {
            throw AiOwnershipSupport.missing("AI会话");
        }
        return session;
    }

    private void deleteHistory(AiSession session) {
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, session.getId())
                .eq(AiMessage::getTenantId, session.getTenantId()));
        conversationMapper.delete(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getSessionId, session.getId().toString())
                .eq(AiConversation::getUserId, session.getUserId())
                .eq(AiConversation::getTenantId, session.getTenantId()));
    }

    private SessionVO convertToVO(AiSession session) {
        SessionVO vo = new SessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }
}
