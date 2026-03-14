package com.lotus.bixi.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.vo.SessionVO;

import java.util.List;

public interface SessionService extends IService<AiSession> {

    List<SessionVO> listSessions();

    SessionVO createSession(SessionDTO dto);

    SessionVO updateSession(SessionDTO dto);

    void deleteSession(Long id);

    SessionVO getSession(Long id);
}
