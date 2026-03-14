package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.ai.util.AiInputValidator;
import com.lotus.bixi.common.ai.util.SensitiveDataFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 对话服务实现
 *
 * @author bixi
 * @date 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements ChatService {

    private final ChatClient chatClient;
    private final VectorStoreService vectorStoreService;

    @Override
    public ChatVO chat(ChatDTO dto) {
        validateChatDTO(dto);
        try {
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
        } catch (Exception e) {
            log.error("Chat error", e);
            throw new AiException("AI 对话服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> streamChat(ChatDTO dto) {
        validateChatDTO(dto);
        String filteredMessage = SensitiveDataFilter.filter(dto.getMessage());
        String sessionId = dto.getSessionId() != null ? dto.getSessionId() : UUID.randomUUID().toString();
        String model = dto.getModel() != null ? dto.getModel() : AiConstants.DEFAULT_MODEL;

        StringBuilder answerBuilder = new StringBuilder();

        return chatClient.prompt()
                .user(filteredMessage)
                .stream()
                .content()
                .doOnNext(content -> {
                    if (content != null) {
                        answerBuilder.append(content);
                    }
                })
                .doOnComplete(() -> {
                    saveConversation(sessionId, filteredMessage, answerBuilder.toString(), model, "stream_chat");
                })
                .doOnError(e -> {
                    log.error("Stream chat error", e);
                    throw new AiException("AI 流式对话异常: " + e.getMessage(), e);
                });
    }

    @Override
    public ChatVO ragChat(ChatDTO dto) {
        validateChatDTO(dto);
        try {
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
        } catch (Exception e) {
            log.error("RAG Chat error", e);
            throw new AiException("RAG 对话服务异常: " + e.getMessage(), e);
        }
    }

    private void validateChatDTO(ChatDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ChatDTO cannot be null");
        }
        AiInputValidator.validate(dto.getMessage());
    }

    /**
     * Build context for RAG.
     *
     * @param query The user query
     * @return Context string
     */
    private String buildContext(String query) {
        try {
            SearchDTO searchDTO = new SearchDTO();
            searchDTO.setQuery(query);
            searchDTO.setTopK(3); // Default topK
            List<DocumentVO> documents = vectorStoreService.similaritySearch(searchDTO);
            if (documents == null || documents.isEmpty()) {
                log.warn("No relevant documents found for query: {}", query);
                return "";
            }
            return documents.stream()
                    .map(DocumentVO::getContent)
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Error building context from vector store", e);
            return ""; // Fallback to empty context
        }
    }

    /**
     * Build prompt with context for RAG.
     *
     * @param query   The user query
     * @param context The retrieved context
     * @return Enhanced prompt
     */
    private String buildRagPrompt(String query, String context) {
        if (context == null || context.isEmpty()) {
            return query;
        }
        return String.format("基于以下上下文信息回答问题：\n\n上下文：\n%s\n\n问题：%s", context, query);
    }

    /**
     * Save conversation record to database.
     *
     * @param sessionId Session ID
     * @param question  User question
     * @param answer    AI answer
     * @param model     Model used
     * @param type      Conversation type (chat/rag)
     */
    private void saveConversation(String sessionId, String question, String answer, String model, String type) {
        try {
            AiConversation conversation = new AiConversation();
            conversation.setSessionId(sessionId);
            conversation.setQuestion(question);
            conversation.setAnswer(answer);
            conversation.setModel(model);
            conversation.setConversationType(type);
            this.save(conversation);
        } catch (Exception e) {
            log.error("Failed to save conversation", e);
            // Do not rethrow, as this is a side effect
        }
    }
}
