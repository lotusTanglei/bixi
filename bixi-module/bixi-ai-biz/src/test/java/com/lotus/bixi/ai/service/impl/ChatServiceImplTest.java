package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.ai.mapper.AiConversationMapper;
import com.lotus.bixi.ai.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private AiConversationMapper conversationMapper;

    @Mock
    private VectorStoreService vectorStoreService;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        // Setup ChatClient mock chain
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(responseSpec);
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
        verify(chatClient, times(1)).prompt();
        verify(requestSpec, times(1)).user(anyString());
        verify(requestSpec, times(1)).call();
        verify(responseSpec, times(1)).content();
    }
}
