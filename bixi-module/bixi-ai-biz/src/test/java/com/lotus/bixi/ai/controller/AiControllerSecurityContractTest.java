package com.lotus.bixi.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.ai.mapper.AiModelConfigMapper;
import com.lotus.bixi.ai.service.impl.ModelConfigServiceImpl;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiControllerSecurityContractTest {

    @BeforeEach
    void authenticateModelConfigContract() {
        var authority = new SimpleGrantedAuthority("ai_test");
        var user = new BixiUser(11L, 1L, 1L, "contract-test", "unused", null,
                true, true, true, true, java.util.List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, java.util.List.of(authority)));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyAiEndpointDeclaresItsStablePermission() throws Exception {
        Map<String, String> ai = Map.ofEntries(
                Map.entry("chat", "ai_chat_add"),
                Map.entry("streamChat", "ai_chat_add"),
                Map.entry("ragChat", "ai_rag_add"),
                Map.entry("addDocument", "ai_document_add"),
                Map.entry("addDocuments", "ai_document_add"),
                Map.entry("pageDocuments", "ai_document_view"),
                Map.entry("listDocuments", "ai_document_view"),
                Map.entry("uploadDocument", "ai_document_add"),
                Map.entry("search", "ai_document_view"),
                Map.entry("deleteDocument", "ai_document_del"));
        Map<String, String> session = Map.ofEntries(
                Map.entry("listSessions", "ai_session_view"),
                Map.entry("createSession", "ai_session_add"),
                Map.entry("updateSession", "ai_session_edit"),
                Map.entry("deleteSession", "ai_session_del"),
                Map.entry("getSession", "ai_session_view"),
                Map.entry("listMessages", "ai_message_view"),
                Map.entry("sendMessage", "ai_message_add"),
                Map.entry("sendRagMessage", "ai_message_add"),
                Map.entry("deleteMessages", "ai_message_del"),
                Map.entry("getConfig", "ai_config_view"),
                Map.entry("listModels", "ai_config_view"),
                Map.entry("updateConfig", "ai_config_edit"));

        assertPermissions(AiController.class, ai);
        assertPermissions(AiSessionController.class, session);
    }

    @Test
    void everyAiMutationDeclaresAStableAuditTitle() {
        assertAuditTitles(AiController.class, Map.ofEntries(
                Map.entry("chat", "AI同步对话"),
                Map.entry("streamChat", "AI流式对话"),
                Map.entry("ragChat", "AI知识库对话"),
                Map.entry("addDocument", "新增AI文档"),
                Map.entry("addDocuments", "批量新增AI文档"),
                Map.entry("uploadDocument", "上传AI文档"),
                Map.entry("deleteDocument", "删除AI文档")));
        assertAuditTitles(AiSessionController.class, Map.ofEntries(
                Map.entry("createSession", "创建AI会话"),
                Map.entry("updateSession", "更新AI会话"),
                Map.entry("deleteSession", "删除AI会话"),
                Map.entry("sendMessage", "发送AI消息"),
                Map.entry("sendRagMessage", "发送AI知识库消息"),
                Map.entry("deleteMessages", "删除AI会话消息"),
                Map.entry("updateConfig", "更新AI模型配置")));
    }

    @Test
    void modelConfigurationResponseNeverContainsAnApiKeyOrSecret() throws Exception {
        AiModelConfigMapper mapper = mock(AiModelConfigMapper.class);
        String json = new ObjectMapper().writeValueAsString(new ModelConfigServiceImpl(mapper).getConfig());

        assertThat(json).doesNotContainIgnoringCase("apiKey")
                .doesNotContainIgnoringCase("secret")
                .doesNotContain("DASHSCOPE_API_KEY");
    }

    private static void assertPermissions(Class<?> controller, Map<String, String> expected) {
        assertThat(controller.getDeclaredMethods())
                .filteredOn(method -> expected.containsKey(method.getName()))
                .hasSize(expected.size())
                .allSatisfy(method -> assertThat(method.getAnnotation(HasPermission.class))
                        .as(method.getName())
                        .isNotNull()
                        .extracting(HasPermission::value)
                        .isEqualTo(new String[]{expected.get(method.getName())}));
    }

    private static void assertAuditTitles(Class<?> controller, Map<String, String> expected) {
        for (Method method : controller.getDeclaredMethods()) {
            if (!expected.containsKey(method.getName())) {
                continue;
            }
            assertThat(method.getAnnotation(SysLog.class))
                    .as(method.getName())
                    .isNotNull()
                    .extracting(SysLog::value)
                    .isEqualTo(expected.get(method.getName()));
        }
    }
}
