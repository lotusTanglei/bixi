package com.lotus.bixi.ai.service;

import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.vo.MessageVO;

import java.util.List;

public interface MessageService {

    List<MessageVO> listMessages(Long sessionId);

    MessageVO sendMessage(MessageDTO dto);

    MessageVO sendRagMessage(MessageDTO dto);

    void deleteMessages(Long sessionId);
}
