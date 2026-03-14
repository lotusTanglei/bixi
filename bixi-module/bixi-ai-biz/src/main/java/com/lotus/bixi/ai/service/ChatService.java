package com.lotus.bixi.ai.service;

import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.vo.ChatVO;

/**
 * AI 对话服务接口
 *
 * @author bixi
 * @date 2025-01-01
 */
public interface ChatService {

    ChatVO chat(ChatDTO dto);

    ChatVO ragChat(ChatDTO dto);
}
