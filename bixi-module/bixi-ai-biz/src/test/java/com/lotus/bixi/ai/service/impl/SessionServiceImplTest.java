package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    private SessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(mock(AiMessageMapper.class), mock(AiConversationMapper.class));
        TenantContextHolder.set(9L);
        var authority = new SimpleGrantedAuthority("ai_chat_add");
        var user = new BixiUser(11L, 1L, 9L, "session-test", "unused", null,
                true, true, true, true, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority)));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSessionReportsPersistenceFailure() {
        sessionService = spy(sessionService);
        doReturn(false).when(sessionService).save(any(AiSession.class));

        SessionDTO dto = new SessionDTO();
        dto.setTitle("failed session");

        assertThatThrownBy(() -> sessionService.createSession(dto))
                .isInstanceOf(AiException.class)
                .hasMessage("AI会话保存失败");
    }

    @Test
    void updateSessionReportsPersistenceFailure() {
        AiSession existing = new AiSession();
        existing.setId(7L);
        existing.setUserId(11L);
        existing.setTenantId(9L);
        existing.setDelFlag("0");
        sessionService = spy(sessionService);
        doReturn(existing).when(sessionService).requireOwnedSession(7L);
        doReturn(false).when(sessionService).updateById(any(AiSession.class));

        SessionDTO dto = new SessionDTO();
        dto.setId(7L);
        dto.setTitle("updated title");

        assertThatThrownBy(() -> sessionService.updateSession(dto))
                .isInstanceOf(AiException.class)
                .hasMessage("AI会话更新失败");
    }
}
