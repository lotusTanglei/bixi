package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.ai.util.AiInputValidator;
import com.lotus.bixi.common.ai.util.SensitiveDataFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * AI 对话服务实现
 *
 * @author bixi
 * @date 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final AiConversationMapper conversationMapper;
    private final VectorStoreService vectorStoreService;

    @Override
    public ChatVO chat(ChatDTO dto) {
        AiInputValidator.validate(dto.getMessage());
        String filteredMessage = SensitiveDataFilter.filter(dto.getMessage());
        String sessionId = dto.getSessionId() != null ? dto.getSessionId() : UUID.randomUUID().toString();
        String model = dto.getModel() != null ? dto.getModel() : AiConstants.DEFAULT_MODEL;
        String answer = chatClient.prompt()
                .user(filteredMessage)
                .call()
                .content();
        saveConversation(sessionId, filteredMessage, answer, model, "chat");
        ChatVO vo = new ChatVO();
        vo.setContent(answer);
        vo.setSessionId(sessionId);
        vo.setModel(model);
        vo.setFinished(true);
        return vo;
    }

    @Override
    public ChatVO ragChat(ChatDTO dto) {
        AiInputValidator.validate(dto.getMessage());
        String filteredMessage = SensitiveDataFilter.filter(dto.getMessage());
        String sessionId = dto.getSessionId() != null ? dto.getSessionId() : UUID.randomUUID().toString();
        String model = dto.getModel() != null ? dto.getModel() : AiConstants.DEFAULT_MODEL;
        String context = buildContext(filteredMessage);
        String enhancedPrompt = buildRagPrompt(filteredMessage, context);
        String answer = chatClient.prompt()
                .user(enhancedPrompt)
                .call()
                .content();
        saveConversation(sessionId, filteredMessage, answer, model, "rag");
        ChatVO vo = new ChatVO();
        vo.setContent(answer);
        vo.setSessionId(sessionId);
        vo.setModel(model);
        vo.setFinished(true);
        return vo;
    }

    private String buildContext(String query) {
        return "";
    }

    private String buildRagPrompt(String query, String context) {
        if (context == null || context.isEmpty()) {
            return query;
        }
        return String.format("基于以下上下文信息回答问题：\n\n上下文：\n%s\n\n问题：%s", context, query);
    }

    private void saveConversation(String sessionId, String question, String answer, String model, String type) {
        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);
        conversation.setModel(model);
        conversation.setConversationType(type);
        conversationMapper.insert(conversation);
    }
}
