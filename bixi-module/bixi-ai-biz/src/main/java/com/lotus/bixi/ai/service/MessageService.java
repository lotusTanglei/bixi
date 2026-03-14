package com.lotus.bixi.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.vo.MessageVO;

import java.util.List;

public interface MessageService extends IService<AiMessage> {

    List<MessageVO> listMessages(Long sessionId);

    MessageVO sendMessage(MessageDTO dto);

    MessageVO sendRagMessage(MessageDTO dto);

    void deleteMessages(Long sessionId);
}
