package com.lotus.bixi.ai.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.api.vo.SessionVO;
import com.lotus.bixi.ai.api.vo.SourceVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.ai.util.AiInputValidator;
import com.lotus.bixi.common.ai.util.SensitiveDataFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
@ConditionalOnAiEnabled
public class ChatServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements ChatService {

    private final ChatClient chatClient;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final VectorStoreService vectorStoreService;
    private final SessionService sessionService;

    private final ModelConfigService modelConfigService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatVO chat(ChatDTO dto) {
        AiOwnershipSupport.requireWritable();
        var owner = AiOwnershipSupport.requireUser();
        Long tenantId = AiOwnershipSupport.tenantId();
        validateChatDTO(dto);
        try {
            String filteredMessage = SensitiveDataFilter.filter(dto.getMessage());
            String sessionId = resolveSessionId(dto);
            ResolvedChatRequest request = resolveRequest(dto);
            String answer = prompt(request)
                    .user(filteredMessage)
                    .call()
                    .content();
            saveConversation(sessionId, filteredMessage, answer, request.model(), "chat", owner.getId(), tenantId,
                    List.of());
            ChatVO vo = new ChatVO();
            vo.setContent(answer);
            vo.setSessionId(sessionId);
            vo.setModel(request.model());
            vo.setFinished(true);
            return vo;
        } catch (AccessDeniedException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat error", e);
            throw new AiException("AI 对话服务异常: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> streamChat(ChatDTO dto) {
        AiOwnershipSupport.requireWritable();
        // The provider may emit on a different thread after the HTTP request
        // has returned. Capture the trusted identity before subscribing so the
        // recovery row never depends on a request-thread SecurityContext.
        var owner = AiOwnershipSupport.requireUser();
        Long tenantId = AiOwnershipSupport.tenantId();
        Long ownerId = owner.getId();
        validateChatDTO(dto);
        String filteredMessage = SensitiveDataFilter.filter(dto.getMessage());
        String sessionId = resolveSessionId(dto);
        ResolvedChatRequest request = resolveRequest(dto);

        StringBuilder answerBuilder = new StringBuilder();
        AtomicBoolean conversationSaved = new AtomicBoolean();

        return prompt(request)
                .user(filteredMessage)
                .stream()
                .content()
                .doOnNext(content -> {
                    if (content != null) {
                        answerBuilder.append(content);
                    }
                })
                .doOnComplete(() -> {
                    if (conversationSaved.compareAndSet(false, true)) {
                        saveConversation(sessionId, filteredMessage, answerBuilder.toString(), request.model(),
                                "stream_chat", ownerId, tenantId, List.of());
                    }
                })
                .doOnError(e -> {
                    log.error("Stream chat error", e);
                    if (conversationSaved.compareAndSet(false, true)) {
                        saveConversation(sessionId, filteredMessage, answerBuilder.toString(), request.model(),
                                "stream_error", ownerId, tenantId, List.of());
                    }
                })
                .doOnCancel(() -> {
                    if (conversationSaved.compareAndSet(false, true)) {
                        saveConversation(sessionId, filteredMessage, answerBuilder.toString(), request.model(),
                                "stream_cancelled", ownerId, tenantId, List.of());
                    }
                })
                .onErrorMap(e -> e instanceof AiException
                        ? e
                        : new AiException("AI 流式对话异常: " + e.getMessage(), e));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatVO ragChat(ChatDTO dto) {
        AiOwnershipSupport.requireWritable();
        var owner = AiOwnershipSupport.requireUser();
        Long tenantId = AiOwnershipSupport.tenantId();
        validateChatDTO(dto);
        try {
            String filteredMessage = SensitiveDataFilter.filter(dto.getMessage());
            String sessionId = resolveSessionId(dto);
            ResolvedChatRequest request = resolveRequest(dto);
            List<DocumentVO> contextDocuments = findContextDocuments(filteredMessage, dto.getDocumentIds());
            String context = contextDocuments.stream()
                    .map(document -> StringUtils.hasText(document.getSnippet())
                            ? document.getSnippet() : document.getContent())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n\n"));
            String enhancedPrompt = buildRagPrompt(filteredMessage, context);
            String answer = prompt(request)
                    .user(enhancedPrompt)
                    .call()
                    .content();
            saveConversation(sessionId, filteredMessage, answer, request.model(), "rag", owner.getId(), tenantId,
                    toSources(contextDocuments));
            ChatVO vo = new ChatVO();
            vo.setContent(answer);
            vo.setSessionId(sessionId);
            vo.setModel(request.model());
            vo.setFinished(true);
            vo.setSources(toSources(contextDocuments));
            return vo;
        } catch (AccessDeniedException | IllegalArgumentException e) {
            throw e;
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
     * Bind every conversation to a persisted, owner-checked AI session.
     * Missing IDs retain the convenient chat API by creating a session first;
     * arbitrary UUID/string keys are rejected instead of creating orphaned
     * conversation rows that have no owner boundary.
     */
    private String resolveSessionId(ChatDTO dto) {
        String requestedSessionId = dto == null ? null : dto.getSessionId();
        if (!StringUtils.hasText(requestedSessionId)) {
            SessionDTO session = new SessionDTO();
            session.setTitle(sessionTitle(dto == null ? null : dto.getMessage()));
            SessionVO created = sessionService.createSession(session);
            if (created == null || created.getId() == null) {
                throw AiOwnershipSupport.missing("AI会话");
            }
            return created.getId().toString();
        }

        String normalized = requestedSessionId.trim();
        final long sessionId;
        try {
            sessionId = Long.parseLong(normalized);
        }
        catch (NumberFormatException ex) {
            throw AiOwnershipSupport.missing("AI会话");
        }
        if (sessionId <= 0) {
            throw AiOwnershipSupport.missing("AI会话");
        }
        sessionService.requireOwnedSession(sessionId);
        return Long.toString(sessionId);
    }

    private String sessionTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return "AI对话";
        }
        String normalized = message.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private ResolvedChatRequest resolveRequest(ChatDTO dto) {
        var config = modelConfigService.getConfig();
        String model = StringUtils.hasText(dto.getModel()) ? dto.getModel() : config.getCurrentModel();
        if (!StringUtils.hasText(model)) {
            model = AiConstants.DEFAULT_MODEL;
        }
        String resolvedModel = model;
        boolean allowed = config.getAvailableModels() != null && config.getAvailableModels().stream()
                .anyMatch(candidate -> resolvedModel.equals(candidate.getId()));
        if (!allowed) {
            throw new IllegalArgumentException("不支持的AI模型: " + resolvedModel);
        }
        Double temperature = dto.getTemperature() != null ? dto.getTemperature() : config.getTemperature();
        Integer maxTokens = dto.getMaxTokens() != null ? dto.getMaxTokens() : config.getMaxTokens();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(resolvedModel)
                .withTemperature(temperature)
                .withMaxToken(maxTokens)
                .withTopP(config.getTopP())
                .build();
        return new ResolvedChatRequest(resolvedModel, options, config.getSystemPrompt());
    }

    private ChatClientRequestSpec prompt(ResolvedChatRequest request) {
        ChatClientRequestSpec prompt = chatClient.prompt().options(request.options());
        if (StringUtils.hasText(request.systemPrompt())) {
            prompt = prompt.system(request.systemPrompt());
        }
        return prompt;
    }

    /**
     * Build context for RAG.
     *
     * @param query The user query
     * @return Context string
     */
    private List<DocumentVO> findContextDocuments(String query, List<Long> documentIds) {
        try {
            SearchDTO searchDTO = new SearchDTO();
            searchDTO.setQuery(query);
            searchDTO.setTopK(3); // Default topK
            searchDTO.setDocumentIds(documentIds);
            List<DocumentVO> documents = vectorStoreService.similaritySearch(searchDTO);
            if (documents == null || documents.isEmpty()) {
                log.warn("No relevant documents found for query: {}", query);
                return List.of();
            }
            return documents;
        } catch (Exception e) {
            log.error("Error building context from vector store", e);
            throw new AiException("RAG 检索失败", e);
        }
    }

    private List<SourceVO> toSources(List<DocumentVO> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream().map(document -> {
            SourceVO source = new SourceVO();
            source.setDocumentId(document.getId());
            source.setDocumentName(document.getTitle());
            source.setContent(document.getSnippet() != null ? document.getSnippet() : document.getContent());
            source.setScore(document.getScore());
            source.setChunkIndex(document.getChunkIndex());
            return source;
        }).toList();
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
     * Keep the legacy conversation audit row and the session history in sync.
     * The public chat endpoints historically wrote only {@code ai_conversation},
     * while the session history endpoint reads {@code ai_message}; writing both
     * rows here makes refreshes and deletes observe the same conversation.
     */
    private void saveConversation(String sessionId, String question, String answer, String model, String type,
                                  Long userId, Long tenantId, List<SourceVO> sources) {
        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);
        conversation.setModel(model);
        conversation.setConversationType(type);
        conversation.setUserId(userId);
        conversation.setTenantId(tenantId);
        try {
            if (!this.save(conversation)) {
                log.error("Failed to save conversation: no row was inserted");
                throw new AiException("AI 对话记录保存失败");
            }
            long numericSessionId = parseSessionId(sessionId);
            AiMessage userMessage = new AiMessage();
            userMessage.setSessionId(numericSessionId);
            userMessage.setRole("user");
            userMessage.setContent(question);
            userMessage.setCreateBy(userId);
            userMessage.setTenantId(tenantId);
            insertMessage(userMessage);

            AiMessage assistantMessage = new AiMessage();
            assistantMessage.setSessionId(numericSessionId);
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(answer == null ? "" : answer);
            assistantMessage.setCreateBy(userId);
            assistantMessage.setTenantId(tenantId);
            assistantMessage.setSources(serializeSources(sources));
            insertMessage(assistantMessage);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to save conversation", e);
            throw new AiException("AI 对话记录保存失败", e);
        }
    }

    private void insertMessage(AiMessage message) {
        try {
            if (messageMapper.insert(message) <= 0) {
                throw new AiException("AI消息保存失败");
            }
        }
        catch (AiException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new AiException("AI消息保存失败", e);
        }
    }

    private long parseSessionId(String sessionId) {
        try {
            long value = Long.parseLong(sessionId);
            if (value > 0) {
                return value;
            }
        }
        catch (NumberFormatException ignored) {
            // resolveSessionId normally prevents this; keep persistence guarded
            // if a caller bypasses that path in a future implementation.
        }
        throw new AiException("AI会话不存在");
    }

    private String serializeSources(List<SourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        }
        catch (JsonProcessingException e) {
            throw new AiException("AI引用来源保存失败", e);
        }
    }

    private record ResolvedChatRequest(String model, DashScopeChatOptions options, String systemPrompt) {
    }
}
