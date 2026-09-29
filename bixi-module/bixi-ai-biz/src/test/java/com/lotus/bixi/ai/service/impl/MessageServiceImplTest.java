package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.api.vo.MessageVO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private SessionService sessionService;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private AiMessageMapper messageMapper;

    @Mock
    private AiConversationMapper conversationMapper;

    @InjectMocks
    private MessageServiceImpl messageService;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(responseSpec);
        lenient().when(messageMapper.insert(any(AiMessage.class))).thenReturn(1);
        lenient().when(modelConfigService.getConfig()).thenReturn(modelConfig());
        lenient().when(sessionService.requireOwnedSession(anyLong())).thenReturn(null);
        ReflectionTestUtils.setField(messageService, "baseMapper", messageMapper);

        TenantContextHolder.set(1L);
        var authorities = List.of(new SimpleGrantedAuthority("ai_test"));
        var user = new BixiUser(11L, 1L, 1L, "message-test", "unused", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void ragRetrievalFailureIsReportedWithoutCallingTheProvider() {
        when(vectorStoreService.similaritySearch(any(SearchDTO.class)))
                .thenThrow(new IllegalStateException("vector store unavailable"));

        MessageDTO dto = message("message retrieval failure");
        dto.setDocumentIds(List.of(7L));

        assertThatThrownBy(() -> messageService.sendRagMessage(dto))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("RAG 检索失败");
        verify(chatClient, never()).prompt();
    }

    @Test
    void ragNoHitsStillCallsTheProviderWithoutSources() {
        when(vectorStoreService.similaritySearch(any(SearchDTO.class))).thenReturn(List.of());
        when(responseSpec.content()).thenReturn("answer without retrieved context");

        MessageVO result = messageService.sendRagMessage(message("message with no hits"));

        assertThat(result.getContent()).isEqualTo("answer without retrieved context");
        assertThat(result.getSources()).isEmpty();
        verify(requestSpec).user("message with no hits");
    }

    @Test
    void ragWithoutASelectionSearchesAllDocumentsVisibleToTheOwner() {
        var selected = new com.lotus.bixi.ai.api.vo.DocumentVO();
        selected.setId(7L);
        selected.setTitle("owner knowledge");
        selected.setSnippet("visible owner context");
        selected.setChunkIndex(2);
        when(vectorStoreService.similaritySearch(any(SearchDTO.class))).thenReturn(List.of(selected));
        when(responseSpec.content()).thenReturn("answer with owner context");

        MessageDTO dto = message("search all visible documents");
        dto.setDocumentIds(null);
        MessageVO result = messageService.sendRagMessage(dto);

        assertThat(result.getSources()).extracting("documentId").containsExactly(7L);
        assertThat(result.getSources()).extracting("chunkIndex").containsExactly(2);
        verify(vectorStoreService).similaritySearch(org.mockito.ArgumentMatchers.argThat(search ->
                search.getDocumentIds() == null && "search all visible documents".equals(search.getQuery())));
        verify(requestSpec).user(org.mockito.ArgumentMatchers.contains("visible owner context"));
    }

    @Test
    void userMessageInsertFailureIsReportedBeforeCallingProvider() {
        when(messageMapper.insert(any(AiMessage.class))).thenReturn(0);

        assertThatThrownBy(() -> messageService.sendMessage(message("persist failure")))
                .isInstanceOf(AiException.class)
                .hasMessage("AI消息保存失败");
        verify(chatClient, never()).prompt();
    }

    @Test
    void assistantMessageInsertFailureIsReportedAndRowsCarryTrustedOwner() {
        when(messageMapper.insert(any(AiMessage.class))).thenReturn(1, 0);
        when(responseSpec.content()).thenReturn("answer");

        assertThatThrownBy(() -> messageService.sendMessage(message("assistant persist failure")))
                .isInstanceOf(AiException.class)
                .hasMessage("AI消息保存失败");

        var inserted = org.mockito.ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, times(2)).insert(inserted.capture());
        assertThat(inserted.getAllValues()).allSatisfy(row -> {
            assertThat(row.getCreateBy()).isEqualTo(11L);
            assertThat(row.getTenantId()).isEqualTo(1L);
            assertThat(row.getSessionId()).isEqualTo(101L);
        });
    }

    private static MessageDTO message(String content) {
        MessageDTO dto = new MessageDTO();
        dto.setSessionId(101L);
        dto.setContent(content);
        dto.setDocumentIds(List.of(7L));
        return dto;
    }

    private static ModelConfigVO modelConfig() {
        ModelConfigVO config = new ModelConfigVO();
        config.setCurrentModel("qwen-plus");
        config.setTemperature(0.7);
        config.setMaxTokens(2000);
        config.setTopP(0.9);
        ModelConfigVO.ModelInfo model = new ModelConfigVO.ModelInfo();
        model.setId("qwen-plus");
        config.setAvailableModels(List.of(model));
        return config;
    }
}
