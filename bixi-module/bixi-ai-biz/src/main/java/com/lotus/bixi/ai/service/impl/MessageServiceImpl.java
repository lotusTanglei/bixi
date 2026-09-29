package com.lotus.bixi.ai.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.api.vo.MessageVO;
import com.lotus.bixi.ai.api.vo.SourceVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.ai.service.MessageService;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.ai.util.AiInputValidator;
import com.lotus.bixi.common.ai.util.SensitiveDataFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnAiEnabled
public class MessageServiceImpl extends ServiceImpl<AiMessageMapper, AiMessage> implements MessageService {

    private final ChatClient chatClient;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;
    private final SessionService sessionService;
    private final ModelConfigService modelConfigService;
    private final AiConversationMapper conversationMapper;

    @Override
    public List<MessageVO> listMessages(Long sessionId) {
        sessionService.requireOwnedSession(sessionId);
        List<AiMessage> messages = lambdaQuery()
                .eq(AiMessage::getSessionId, sessionId)
                .eq(AiMessage::getDelFlag, "0")
                .orderByAsc(AiMessage::getCreateTime)
                .list();
        return messages.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(MessageDTO dto) {
        AiOwnershipSupport.requireWritable();
        validateMessage(dto);
        AiInputValidator.validate(dto.getContent());
        String filteredContent = SensitiveDataFilter.filter(dto.getContent());

        AiMessage userMessage = new AiMessage();
        userMessage.setSessionId(dto.getSessionId());
        userMessage.setRole("user");
        userMessage.setContent(filteredContent);
        persistMessage(userMessage);

        String answer = prompt(dto)
                .user(filteredContent)
                .call()
                .content();

        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setSessionId(dto.getSessionId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        persistMessage(assistantMessage);

        return convertToVO(assistantMessage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendRagMessage(MessageDTO dto) {
        AiOwnershipSupport.requireWritable();
        validateMessage(dto);
        AiInputValidator.validate(dto.getContent());
        String filteredContent = SensitiveDataFilter.filter(dto.getContent());

        AiMessage userMessage = new AiMessage();
        userMessage.setSessionId(dto.getSessionId());
        userMessage.setRole("user");
        userMessage.setContent(filteredContent);
        persistMessage(userMessage);

        RagContext ragContext = retrieveContext(dto.getContent(), dto.getDocumentIds());
        String enhancedPrompt = buildRagPrompt(filteredContent, ragContext.context());

        String answer = prompt(dto)
                .user(enhancedPrompt)
                .call()
                .content();

        List<SourceVO> sources = ragContext.sources();

        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setSessionId(dto.getSessionId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        assistantMessage.setSources(writeSources(sources));
        persistMessage(assistantMessage);

        MessageVO vo = convertToVO(assistantMessage);
        vo.setSources(sources);

        return vo;
    }

    private void validateMessage(MessageDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("消息不能为空");
        }
        sessionService.requireOwnedSession(dto.getSessionId());
    }

    /**
     * Keep the user and assistant rows in the same transaction as the model
     * call. A false insert result must never be reported as a successful
     * message, and the trusted owner context is written explicitly so direct
     * service invocations do not depend on request-thread fill handlers.
     */
    private void persistMessage(AiMessage message) {
        var user = AiOwnershipSupport.requireUser();
        message.setCreateBy(user.getId());
        message.setTenantId(AiOwnershipSupport.tenantId());
        try {
            if (!save(message)) {
                throw new AiException("AI消息保存失败");
            }
        }
        catch (AiException failure) {
            throw failure;
        }
        catch (RuntimeException failure) {
            log.error("Failed to save AI message", failure);
            throw new AiException("AI消息保存失败", failure);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessages(Long sessionId) {
        AiOwnershipSupport.requireWritable();
        AiSession session = sessionService.requireOwnedSession(sessionId);
        lambdaUpdate()
                .eq(AiMessage::getSessionId, sessionId)
                .eq(AiMessage::getTenantId, session.getTenantId())
                .remove();
        conversationMapper.delete(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getSessionId, sessionId.toString())
                .eq(AiConversation::getUserId, session.getUserId())
                .eq(AiConversation::getTenantId, session.getTenantId()));
    }

    /** Resolve the persisted model settings before every message request. */
    private ChatClientRequestSpec prompt(MessageDTO dto) {
        var config = modelConfigService.getConfig();
        String model = StringUtils.hasText(dto.getModel()) ? dto.getModel() : config.getCurrentModel();
        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("未配置AI模型");
        }
        boolean allowed = config.getAvailableModels() != null && config.getAvailableModels().stream()
                .anyMatch(candidate -> model.equals(candidate.getId()));
        if (!allowed) {
            throw new IllegalArgumentException("不支持的AI模型: " + model);
        }
        Double temperature = dto.getTemperature() != null ? dto.getTemperature() : config.getTemperature();
        Integer maxTokens = dto.getMaxTokens() != null ? dto.getMaxTokens() : config.getMaxTokens();
        Double topP = dto.getTopP() != null ? dto.getTopP() : config.getTopP();
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxToken(maxTokens)
                .withTopP(topP)
                .build();
        ChatClientRequestSpec request = chatClient.prompt().options(options);
        if (StringUtils.hasText(config.getSystemPrompt())) {
            request = request.system(config.getSystemPrompt());
        }
        return request;
    }

    /**
     * Retrieve once and derive both prompt context and persisted citations from
     * the same owner-filtered result set. A second search could rank a
     * different document and make the answer/source pair disagree.
     */
    private RagContext retrieveContext(String query, List<Long> documentIds) {
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setQuery(query);
        searchDTO.setTopK(5);
        // An empty selection means "all documents visible to this owner".
        // Keep the empty list on the search contract instead of silently
        // disabling retrieval; ChatService uses the same semantics.
        searchDTO.setDocumentIds(documentIds == null || documentIds.isEmpty() ? null : documentIds);
        try {
            var results = vectorStoreService.similaritySearch(searchDTO);
            if (results == null || results.isEmpty()) {
                return new RagContext("", List.of());
            }
            String context = results.stream()
                    .map(doc -> StringUtils.hasText(doc.getSnippet()) ? doc.getSnippet() : doc.getContent())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n\n"));
            List<SourceVO> sources = results.stream().map(doc -> {
                SourceVO source = new SourceVO();
                source.setDocumentId(doc.getId());
                source.setDocumentName(doc.getTitle());
                source.setContent(StringUtils.hasText(doc.getSnippet()) ? doc.getSnippet() : doc.getContent());
                source.setScore(doc.getScore());
                source.setChunkIndex(doc.getChunkIndex());
                return source;
            }).toList();
            return new RagContext(context, sources);
        } catch (Exception e) {
            log.error("Build context failed", e);
            throw new AiException("RAG 检索失败", e);
        }
    }

    private String buildRagPrompt(String query, String context) {
        if (context == null || context.isEmpty()) {
            return query;
        }
        return String.format("基于以下上下文信息回答问题：\n\n上下文：\n%s\n\n问题：%s", context, query);
    }

    private String writeSources(List<SourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Serialize RAG sources failed", e);
        }
    }

    private MessageVO convertToVO(AiMessage message) {
        MessageVO vo = new MessageVO();
        BeanUtils.copyProperties(message, vo);
        if (message.getSources() != null && !message.getSources().isEmpty()) {
            try {
                vo.setSources(objectMapper.readValue(message.getSources(), new TypeReference<List<SourceVO>>() {}));
            } catch (JsonProcessingException e) {
                log.error("Parse sources failed", e);
            }
        }
        return vo;
    }

    private record RagContext(String context, List<SourceVO> sources) {
    }
}
