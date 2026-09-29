package com.lotus.bixi.ai.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiConversation;
import com.lotus.bixi.ai.api.entity.AiMessage;
import com.lotus.bixi.ai.api.entity.AiSession;
import com.lotus.bixi.ai.api.exception.AiException;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.api.vo.SessionVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.mapper.AiMessageMapper;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private AiMessageMapper messageMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    @Mock
    private StreamResponseSpec streamResponseSpec;

    @BeforeEach
    void setUp() {
        // Setup ChatClient mock chain
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.options(any(ChatOptions.class))).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(responseSpec);
        lenient().when(requestSpec.stream()).thenReturn(streamResponseSpec);
        lenient().when(conversationMapper.selectList(any())).thenReturn(List.of());
        lenient().when(conversationMapper.insert(any(AiConversation.class))).thenReturn(1);
        lenient().when(messageMapper.insert(any(AiMessage.class))).thenReturn(1);
        SessionVO createdSession = new SessionVO();
        createdSession.setId(101L);
        lenient().when(sessionService.createSession(any())).thenReturn(createdSession);
        lenient().when(sessionService.requireOwnedSession(anyLong())).thenAnswer(invocation -> {
            AiSession session = new AiSession();
            session.setId(invocation.getArgument(0));
            return session;
        });
        ReflectionTestUtils.setField(chatService, "baseMapper", conversationMapper);
        var authority = new SimpleGrantedAuthority("ai_test");
        var user = new BixiUser(11L, 1L, 1L, "chat-test", "unused", null,
                true, true, true, true, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority)));
        ModelConfigVO config = new ModelConfigVO();
        config.setCurrentModel("qwen-plus");
        config.setTemperature(0.7);
        config.setMaxTokens(2000);
        config.setTopP(0.9);
        ModelConfigVO.ModelInfo plus = new ModelConfigVO.ModelInfo();
        plus.setId("qwen-plus");
        ModelConfigVO.ModelInfo max = new ModelConfigVO.ModelInfo();
        max.setId("qwen-max");
        config.setAvailableModels(List.of(plus, max));
        lenient().when(modelConfigService.getConfig()).thenReturn(config);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testChat() {
        // Arrange
        String expectedAnswer = "AI Response";
        when(responseSpec.content()).thenReturn(expectedAnswer);

        ChatDTO dto = new ChatDTO();
        dto.setMessage("Hello");

        // Act
        ChatVO result = chatService.chat(dto);

        // Assert
        assertNotNull(result);
        assertEquals(expectedAnswer, result.getContent());
        assertEquals("101", result.getSessionId());
        verify(chatClient, times(1)).prompt();
        verify(requestSpec, times(1)).user(anyString());
        verify(requestSpec, times(1)).call();
        verify(responseSpec, times(1)).content();
    }

    @Test
    void requestedModelAndSamplingOptionsReachTheProviderRequest() {
        when(responseSpec.content()).thenReturn("configured response");
        ChatDTO dto = new ChatDTO();
        dto.setMessage("Explain the result");
        dto.setModel("qwen-max");
        dto.setTemperature(0.2);
        dto.setMaxTokens(512);

        chatService.chat(dto);

        var options = org.mockito.ArgumentCaptor.forClass(DashScopeChatOptions.class);
        verify(requestSpec).options(options.capture());
        assertThat(options.getValue().getModel()).isEqualTo("qwen-max");
        assertThat(options.getValue().getTemperature()).isEqualTo(0.2);
        assertThat(options.getValue().getMaxTokens()).isEqualTo(512);
        assertThat(options.getValue().getTopP()).isEqualTo(0.9);
    }

    @Test
    void requestedSessionCannotBeUsedByAnotherOwner() {
        var foreignConversation = new com.lotus.bixi.ai.api.entity.AiConversation();
        foreignConversation.setSessionId("foreign-session");
        foreignConversation.setUserId(22L);
        foreignConversation.setTenantId(1L);
        doThrow(AiOwnershipSupport.missing("AI会话"))
                .when(sessionService).requireOwnedSession(999L);

        ChatDTO dto = new ChatDTO();
        dto.setMessage("attempt");
        dto.setSessionId("999");

        assertThatThrownBy(() -> chatService.chat(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI会话不存在");
        verify(chatClient, never()).prompt();
    }

    @Test
    void anonymousChatIsRejectedBeforeCallingTheProvider() {
        SecurityContextHolder.clearContext();
        ChatDTO dto = new ChatDTO();
        dto.setMessage("anonymous");

        assertThatThrownBy(() -> chatService.chat(dto))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatClient, never()).prompt();
    }

    @Test
    void savedConversationUsesTheAuthenticatedOwner() {
        when(responseSpec.content()).thenReturn("owned response");
        ChatDTO dto = new ChatDTO();
        dto.setMessage("owned request");
        dto.setSessionId("101");

        chatService.chat(dto);

        var conversation = org.mockito.ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).insert(conversation.capture());
        assertThat(conversation.getValue().getSessionId()).isEqualTo("101");
        assertThat(conversation.getValue().getUserId()).isEqualTo(11L);
        assertThat(conversation.getValue().getTenantId()).isEqualTo(1L);
    }

    @Test
    void chatAlsoPersistsTheCanonicalSessionHistoryRows() {
        when(responseSpec.content()).thenReturn("owned response");
        ChatDTO dto = new ChatDTO();
        dto.setMessage("owned request");
        dto.setSessionId("101");

        chatService.chat(dto);

        var messages = org.mockito.ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, times(2)).insert(messages.capture());
        assertThat(messages.getAllValues()).extracting(AiMessage::getSessionId)
                .containsOnly(101L);
        assertThat(messages.getAllValues()).extracting(AiMessage::getRole)
                .containsExactly("user", "assistant");
        assertThat(messages.getAllValues()).extracting(AiMessage::getContent)
                .containsExactly("owned request", "owned response");
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.getCreateBy()).isEqualTo(11L);
            assertThat(message.getTenantId()).isEqualTo(1L);
        });
    }

    @Test
    void ragHistoryKeepsTheSameTraceableSourcesReturnedToTheCaller() throws Exception {
        when(responseSpec.content()).thenReturn("scoped response");
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"documentId\":42}]");
        DocumentVO selected = new DocumentVO();
        selected.setId(42L);
        selected.setTitle("selected");
        selected.setSnippet("matched chunk");
        selected.setChunkIndex(2);
        when(vectorStoreService.similaritySearch(any(SearchDTO.class))).thenReturn(List.of(selected));

        ChatDTO dto = new ChatDTO();
        dto.setMessage("question");
        dto.setSessionId("101");
        dto.setDocumentIds(List.of(42L));

        chatService.ragChat(dto);

        var messages = org.mockito.ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper, times(2)).insert(messages.capture());
        assertThat(messages.getAllValues().get(1).getSources()).isEqualTo("[{\"documentId\":42}]");
    }

    @Test
    void chatFailsWhenCanonicalSessionHistoryCannotBeInserted() {
        when(responseSpec.content()).thenReturn("answer that must not be reported as complete");
        when(messageMapper.insert(any(AiMessage.class))).thenReturn(0);

        ChatDTO dto = new ChatDTO();
        dto.setMessage("persist this answer");
        dto.setSessionId("101");

        assertThatThrownBy(() -> chatService.chat(dto))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("AI消息保存失败");
    }

    @Test
    void chatReportsConversationPersistenceException() {
        when(responseSpec.content()).thenReturn("answer that must not be reported as complete");
        doThrow(new IllegalStateException("database unavailable"))
                .when(conversationMapper).insert(any(AiConversation.class));

        ChatDTO dto = new ChatDTO();
        dto.setMessage("persist this answer");
        dto.setSessionId("101");

        assertThatThrownBy(() -> chatService.chat(dto))
                .isInstanceOf(AiException.class);
    }

    @Test
    void chatReportsConversationPersistenceWhenInsertReturnsZero() {
        when(responseSpec.content()).thenReturn("answer that must not be reported as complete");
        when(conversationMapper.insert(any(AiConversation.class))).thenReturn(0);

        ChatDTO dto = new ChatDTO();
        dto.setMessage("persist this answer");
        dto.setSessionId("101");

        assertThatThrownBy(() -> chatService.chat(dto))
                .isInstanceOf(AiException.class);
    }

    @Test
    void streamFailurePersistsPartialAnswerForRecovery() {
        when(streamResponseSpec.content()).thenReturn(Flux.just("partial")
                .concatWith(Flux.error(new IllegalStateException("provider disconnected"))));
        ChatDTO dto = new ChatDTO();
        dto.setMessage("stream request");
        dto.setSessionId("102");

        assertThatThrownBy(() -> chatService.streamChat(dto).collectList().block())
                .isInstanceOf(com.lotus.bixi.ai.api.exception.AiException.class);

        var conversation = org.mockito.ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).insert(conversation.capture());
        assertThat(conversation.getValue().getConversationType()).isEqualTo("stream_error");
        assertThat(conversation.getValue().getAnswer()).isEqualTo("partial");
    }

    @Test
    void streamRecoveryUsesCapturedOwnerWhenProviderEmitsWithoutRequestContext() {
        when(streamResponseSpec.content()).thenReturn(Flux.defer(() -> {
            // A real provider can emit on a worker thread where the request
            // SecurityContext is absent. Persistence must keep the original
            // trusted owner and tenant instead of failing the stream.
            SecurityContextHolder.clearContext();
            return Flux.just("async answer");
        }));
        ChatDTO dto = new ChatDTO();
        dto.setMessage("stream request");
        dto.setSessionId("102");

        assertThat(chatService.streamChat(dto).collectList().block())
                .containsExactly("async answer");

        var conversation = org.mockito.ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).insert(conversation.capture());
        assertThat(conversation.getValue().getConversationType()).isEqualTo("stream_chat");
        assertThat(conversation.getValue().getUserId()).isEqualTo(11L);
        assertThat(conversation.getValue().getTenantId()).isEqualTo(1L);
    }

    @Test
    void streamCancellationPersistsPartialAnswerForRecovery() {
        when(streamResponseSpec.content()).thenReturn(Flux.never());
        ChatDTO dto = new ChatDTO();
        dto.setMessage("cancelled stream");
        dto.setSessionId("102");

        var subscription = chatService.streamChat(dto).subscribe();
        subscription.dispose();

        var conversation = org.mockito.ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).insert(conversation.capture());
        assertThat(conversation.getValue().getConversationType()).isEqualTo("stream_cancelled");
        assertThat(conversation.getValue().getAnswer()).isEmpty();
    }

    @Test
    void streamCompletionReportsConversationPersistenceFailure() {
        when(streamResponseSpec.content()).thenReturn(Flux.just("complete"));
        when(conversationMapper.insert(any(AiConversation.class))).thenReturn(0);

        ChatDTO dto = new ChatDTO();
        dto.setMessage("stream request");
        dto.setSessionId("102");

        assertThatThrownBy(() -> chatService.streamChat(dto).collectList().block())
                .isInstanceOf(AiException.class);
        verify(conversationMapper).insert(any(AiConversation.class));
    }

    @Test
    void ragChatUsesTheSelectedDocumentScopeForContextAndSources() {
        when(responseSpec.content()).thenReturn("scoped response");
        DocumentVO selected = new DocumentVO();
        selected.setId(42L);
        selected.setTitle("selected");
        selected.setContent("selected context");
        selected.setChunkIndex(3);
        when(vectorStoreService.similaritySearch(any(SearchDTO.class))).thenReturn(List.of(selected));

        ChatDTO dto = new ChatDTO();
        dto.setMessage("question");
        dto.setSessionId("103");
        ReflectionTestUtils.setField(dto, "documentIds", List.of(42L));

        ChatVO result = chatService.ragChat(dto);

        var search = org.mockito.ArgumentCaptor.forClass(SearchDTO.class);
        verify(vectorStoreService).similaritySearch(search.capture());
        assertThat(search.getValue().getDocumentIds()).containsExactly(42L);
        assertThat(result.getSources()).extracting("documentId").containsExactly(42L);
        assertThat(result.getSources()).extracting("chunkIndex").containsExactly(3);
        verify(requestSpec).user(org.mockito.ArgumentMatchers.contains("selected context"));
    }

    @Test
    void ragPromptUsesTheMatchedChunkInsteadOfTheWholeDocument() {
        when(responseSpec.content()).thenReturn("scoped response");
        DocumentVO selected = new DocumentVO();
        selected.setId(43L);
        selected.setTitle("selected");
        selected.setContent("whole document with unrelated secret");
        selected.setSnippet("matched chunk only");
        when(vectorStoreService.similaritySearch(any(SearchDTO.class))).thenReturn(List.of(selected));

        ChatDTO dto = new ChatDTO();
        dto.setMessage("question");
        dto.setSessionId("104");

        chatService.ragChat(dto);

        verify(requestSpec).user(argThat((String prompt) -> prompt.contains("matched chunk only")
                && !prompt.contains("unrelated secret")));
    }

    @Test
    void ragRetrievalFailureIsReportedWithoutCallingTheProvider() {
        when(vectorStoreService.similaritySearch(any(SearchDTO.class)))
                .thenThrow(new IllegalStateException("vector store unavailable"));

        ChatDTO dto = new ChatDTO();
        dto.setMessage("question");
        dto.setSessionId("105");

        assertThatThrownBy(() -> chatService.ragChat(dto))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("RAG 检索失败");
        verify(chatClient, never()).prompt();
    }

    @Test
    void ragNoHitsStillCallsTheProviderWithoutSources() {
        when(vectorStoreService.similaritySearch(any(SearchDTO.class))).thenReturn(List.of());
        when(responseSpec.content()).thenReturn("answer without retrieved context");

        ChatDTO dto = new ChatDTO();
        dto.setMessage("question");
        dto.setSessionId("106");

        ChatVO result = chatService.ragChat(dto);

        assertThat(result.getContent()).isEqualTo("answer without retrieved context");
        assertThat(result.getSources()).isEmpty();
        verify(requestSpec).user("question");
    }

    @Test
    void arbitraryStringSessionIdsAreRejectedBeforeProviderInvocation() {
        ChatDTO dto = new ChatDTO();
        dto.setMessage("question");
        dto.setSessionId("client-generated-uuid");

        assertThatThrownBy(() -> chatService.chat(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI会话不存在");
        verify(chatClient, never()).prompt();
        verify(sessionService, never()).createSession(any());
    }
}
