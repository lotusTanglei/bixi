package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.vo.MessageVO;
import com.lotus.bixi.ai.api.vo.SourceVO;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.ai.service.MessageService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.ai.util.AiInputValidator;
import com.lotus.bixi.common.ai.util.SensitiveDataFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final AiMessageMapper messageMapper;
    private final ChatClient chatClient;
    private final VectorStoreService vectorStoreService;
    private final ObjectMapper objectMapper;

    @Override
    public List<MessageVO> listMessages(Long sessionId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getSessionId, sessionId)
                .eq(AiMessage::getDelFlag, "0")
                .orderByAsc(AiMessage::getCreateTime);
        List<AiMessage> messages = messageMapper.selectList(wrapper);
        return messages.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public MessageVO sendMessage(MessageDTO dto) {
        AiInputValidator.validate(dto.getContent());
        String filteredContent = SensitiveDataFilter.filter(dto.getContent());

        AiMessage userMessage = new AiMessage();
        userMessage.setSessionId(dto.getSessionId());
        userMessage.setRole("user");
        userMessage.setContent(filteredContent);
        messageMapper.insert(userMessage);

        String answer = chatClient.prompt()
                .user(filteredContent)
                .call()
                .content();

        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setSessionId(dto.getSessionId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        messageMapper.insert(assistantMessage);

        return convertToVO(assistantMessage);
    }

    @Override
    public MessageVO sendRagMessage(MessageDTO dto) {
        AiInputValidator.validate(dto.getContent());
        String filteredContent = SensitiveDataFilter.filter(dto.getContent());

        AiMessage userMessage = new AiMessage();
        userMessage.setSessionId(dto.getSessionId());
        userMessage.setRole("user");
        userMessage.setContent(filteredContent);
        messageMapper.insert(userMessage);

        String context = buildContext(dto.getContent(), dto.getDocumentIds());
        String enhancedPrompt = buildRagPrompt(filteredContent, context);

        String answer = chatClient.prompt()
                .user(enhancedPrompt)
                .call()
                .content();

        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setSessionId(dto.getSessionId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(answer);
        messageMapper.insert(assistantMessage);

        MessageVO vo = convertToVO(assistantMessage);
        if (dto.getDocumentIds() != null && !dto.getDocumentIds().isEmpty()) {
            vo.setSources(buildSources(dto.getContent(), dto.getDocumentIds()));
        }

        return vo;
    }

    @Override
    public void deleteMessages(Long sessionId) {
        LambdaQueryWrapper<AiMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMessage::getSessionId, sessionId);
        messageMapper.delete(wrapper);
    }

    private String buildContext(String query, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "";
        }
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setQuery(query);
        searchDTO.setTopK(5);
        searchDTO.setDocumentIds(documentIds);
        try {
            var results = vectorStoreService.similaritySearch(searchDTO);
            return results.stream()
                    .map(doc -> doc.getContent())
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.error("Build context failed", e);
            return "";
        }
    }

    private String buildRagPrompt(String query, String context) {
        if (context == null || context.isEmpty()) {
            return query;
        }
        return String.format("基于以下上下文信息回答问题：\n\n上下文：\n%s\n\n问题：%s", context, query);
    }

    private List<SourceVO> buildSources(String query, List<Long> documentIds) {
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setQuery(query);
        searchDTO.setTopK(3);
        searchDTO.setDocumentIds(documentIds);
        try {
            var results = vectorStoreService.similaritySearch(searchDTO);
            return results.stream().map(doc -> {
                SourceVO source = new SourceVO();
                source.setDocumentId(doc.getId());
                source.setDocumentName(doc.getTitle());
                source.setContent(doc.getContent());
                source.setScore(doc.getScore());
                return source;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Build sources failed", e);
            return new ArrayList<>();
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
}
