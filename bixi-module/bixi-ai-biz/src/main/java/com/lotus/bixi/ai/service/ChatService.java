package com.lotus.bixi.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.vo.ChatVO;
import reactor.core.publisher.Flux;

/**
 * AI 对话服务接口
 *
 * @author bixi
 * @date 2025-01-01
 */
public interface ChatService extends IService<AiConversation> {

    ChatVO chat(ChatDTO dto);

    Flux<String> streamChat(ChatDTO dto);

    ChatVO ragChat(ChatDTO dto);
}
