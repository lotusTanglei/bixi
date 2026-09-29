package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.MessageDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.dto.SessionDTO;
import com.lotus.bixi.ai.mapper.AiDocumentMapper;
import com.lotus.bixi.ai.service.ChatService;
import com.lotus.bixi.ai.service.MessageService;
import com.lotus.bixi.ai.service.SessionService;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(AiOwnershipIntegrationTest.Config.class)
@TestPropertySource(properties = {
        "mybatis-plus.global-config.banner=false",
        "ai.enabled=true"
})
class AiOwnershipIntegrationTest {

    private static final String MISSING_SESSION = "AI会话不存在";
    private static final String MISSING_DOCUMENT = "AI文档不存在";

    @Autowired SessionService sessions;
    @Autowired MessageService messages;
    @Autowired ChatService chats;
    @Autowired VectorStoreService documents;
    @Autowired AiDocumentMapper documentMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatClient chatClient;

    @BeforeEach
    void createSchema() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        reset(chatClient);
        ChatClientRequestSpec request = mock(ChatClientRequestSpec.class);
        CallResponseSpec response = mock(CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(request);
        when(request.options(org.mockito.ArgumentMatchers.any())).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(response);
        when(response.content()).thenReturn("assistant-answer");

        jdbc.execute("DROP TABLE IF EXISTS ai_embedding");
        jdbc.execute("DROP TABLE IF EXISTS ai_model_config");
        jdbc.execute("DROP TABLE IF EXISTS ai_document");
        jdbc.execute("DROP TABLE IF EXISTS ai_conversation");
        jdbc.execute("DROP TABLE IF EXISTS ai_message");
        jdbc.execute("DROP TABLE IF EXISTS ai_session");
        jdbc.execute("""
                CREATE TABLE ai_session (
                    id BIGINT PRIMARY KEY, title VARCHAR(255), user_id BIGINT, model VARCHAR(64), status VARCHAR(32),
                    create_by BIGINT, update_by BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
                    del_flag CHAR(1), data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE ai_conversation (
                    id BIGINT PRIMARY KEY, session_id VARCHAR(64), question CLOB, answer CLOB, model VARCHAR(64),
                    token_count INT, conversation_type VARCHAR(32), user_id BIGINT,
                    create_by BIGINT, update_by BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
                    del_flag CHAR(1), status CHAR(1), data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE ai_message (
                    id BIGINT PRIMARY KEY, session_id BIGINT, role VARCHAR(32), content CLOB, token_count INT, sources CLOB,
                    create_by BIGINT, update_by BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
                    del_flag CHAR(1), status CHAR(1), data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE ai_document (
                    id BIGINT PRIMARY KEY, title VARCHAR(255), content CLOB, source VARCHAR(255), doc_type VARCHAR(64),
                    vector_status INT, user_id BIGINT, create_by BIGINT, update_by BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP, del_flag CHAR(1), status CHAR(1),
                    data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE ai_embedding (
                    id BIGINT PRIMARY KEY, document_id BIGINT, vector_id VARCHAR(128), embedding_model VARCHAR(64),
                    embedding CLOB, dimension INT, chunk_index INT, chunk_content CLOB, create_by BIGINT, update_by BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP, del_flag CHAR(1), status CHAR(1),
                    data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE ai_model_config (
                    id BIGINT PRIMARY KEY, current_model VARCHAR(64) NOT NULL DEFAULT 'qwen-plus',
                    temperature DECIMAL(4,3) NOT NULL DEFAULT 0.700, max_tokens INT NOT NULL DEFAULT 2000,
                    top_p DECIMAL(4,3) NOT NULL DEFAULT 0.900, system_prompt CLOB,
                    create_by BIGINT, update_by BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
                    del_flag CHAR(1) DEFAULT '0', status CHAR(1) DEFAULT '0', data_status CHAR(1) DEFAULT '0',
                    tenant_id BIGINT NOT NULL, remark VARCHAR(500),
                    CONSTRAINT uk_ai_model_config_tenant UNIQUE (tenant_id))
                """);
    }

    @AfterEach
    void clearContexts() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void sessionOwnerCanUseItAndAnotherUserCannotReadUpdateOrDeleteIt() {
        login(22L, 1L);
        var owned = sessions.createSession(session("owner-title"));
        SessionDTO ownerUpdate = session("owner-updated");
        ownerUpdate.setId(owned.getId());
        assertThat(sessions.updateSession(ownerUpdate).getTitle()).isEqualTo("owner-updated");

        login(11L, 1L);
        SessionDTO forgedUpdate = session("stolen");
        forgedUpdate.setId(owned.getId());
        assertThatThrownBy(() -> sessions.getSession(owned.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThatThrownBy(() -> sessions.updateSession(forgedUpdate))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThatThrownBy(() -> sessions.deleteSession(owned.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);

        assertThat(jdbc.queryForObject("SELECT title FROM ai_session WHERE id = ?", String.class, owned.getId()))
                .isEqualTo("owner-updated");
        assertThat(jdbc.queryForObject("SELECT del_flag FROM ai_session WHERE id = ?", String.class, owned.getId()))
                .isEqualTo("0");
    }

    @Test
    void messageHistorySendRagAndDeleteRequireTheOwnedParentSession() {
        login(22L, 1L);
        var owned = sessions.createSession(session("messages"));
        messages.sendMessage(message(owned.getId(), "owner-message"));
        assertThat(messages.listMessages(owned.getId())).hasSize(2);

        login(11L, 1L);
        assertThatThrownBy(() -> messages.listMessages(owned.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThatThrownBy(() -> messages.sendMessage(message(owned.getId(), "forged-message")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThatThrownBy(() -> messages.sendRagMessage(message(owned.getId(), "forged-rag")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThatThrownBy(() -> messages.deleteMessages(owned.getId()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_message WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId())).isEqualTo(2);
    }

    @Test
    void deletingSessionAlsoDeletesItsCanonicalAndLegacyHistory() {
        login(22L, 1L);
        var owned = sessions.createSession(session("history-to-delete"));

        ChatDTO request = new ChatDTO();
        request.setSessionId(owned.getId().toString());
        request.setMessage("history entry");
        chats.chat(request);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_message WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId().toString())).isEqualTo(1);

        sessions.deleteSession(owned.getId());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_message WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId().toString())).isZero();
    }

    @Test
    void deletingMessagesAlsoDeletesTheLegacyConversationHistory() {
        login(22L, 1L);
        var owned = sessions.createSession(session("messages-to-delete"));

        ChatDTO request = new ChatDTO();
        request.setSessionId(owned.getId().toString());
        request.setMessage("history entry");
        chats.chat(request);

        messages.deleteMessages(owned.getId());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_message WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE session_id = ? AND del_flag = '0'",
                Integer.class, owned.getId().toString())).isZero();
    }

    @Test
    void chatRejectsCrossUserCrossTenantAndDeletedSessionsBeforeCallingProvider() {
        login(71L, 11L);
        var owned = sessions.createSession(session("chat-owner"));

        ChatDTO ownRequest = new ChatDTO();
        ownRequest.setSessionId(owned.getId().toString());
        ownRequest.setMessage("owner chat");
        assertThat(chats.chat(ownRequest).getSessionId()).isEqualTo(owned.getId().toString());

        login(72L, 11L);
        ChatDTO sameTenant = new ChatDTO();
        sameTenant.setSessionId(owned.getId().toString());
        sameTenant.setMessage("cross user");
        assertThatThrownBy(() -> chats.chat(sameTenant))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);

        login(73L, 12L);
        ChatDTO otherTenant = new ChatDTO();
        otherTenant.setSessionId(owned.getId().toString());
        otherTenant.setMessage("cross tenant");
        assertThatThrownBy(() -> chats.chat(otherTenant))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);

        login(71L, 11L);
        sessions.deleteSession(owned.getId());
        assertThatThrownBy(() -> chats.chat(ownRequest))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_SESSION);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversation", Integer.class)).isEqualTo(1);
    }

    @Test
    void documentWritesUseTheAuthenticatedOwnerAndSearchNeverReturnsOtherUsersOrTenants() throws Exception {
        login(22L, 1L);
        documents.addDocument(document("same-tenant-private", "refund same tenant private"));
        long otherUserDocument = documentId("same-tenant-private");
        embedding(otherUserDocument, 1L, "refund same tenant private");

        login(33L, 2L);
        documents.addDocument(document("other-tenant-private", "refund other tenant private"));
        long otherTenantDocument = documentId("other-tenant-private");
        embedding(otherTenantDocument, 2L, "refund other tenant private");

        login(11L, 1L);
        ObjectMapper requestMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        DocumentDTO forged = requestMapper.readValue("""
                {"title":"owned","content":"refund owner public","source":"test","docType":"txt",
                 "userId":22,"tenantId":2}
                """, DocumentDTO.class);
        documents.addDocument(forged);
        long ownDocument = documentId("owned");
        embedding(ownDocument, 1L, "refund owner public");

        assertThat(jdbc.queryForMap("SELECT user_id, tenant_id FROM ai_document WHERE id = ?", ownDocument))
                .containsEntry("USER_ID", 11L).containsEntry("TENANT_ID", 1L);
        assertThat(documents.listDocuments(null)).extracting("id").containsExactly(ownDocument);

        SearchDTO otherIds = search("refund", List.of(otherUserDocument, otherTenantDocument));
        assertThat(documents.similaritySearch(otherIds)).isEmpty();
        SearchDTO ownId = search("refund owner public", List.of(ownDocument));
        assertThat(documents.similaritySearch(ownId)).extracting("id").containsExactly(ownDocument);

        assertThatThrownBy(() -> documents.deleteDocument(otherUserDocument))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_DOCUMENT);
        assertThatThrownBy(() -> documents.deleteDocument(otherTenantDocument))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(MISSING_DOCUMENT);
        documents.deleteDocument(ownDocument);
        assertThat(jdbc.queryForObject("SELECT del_flag FROM ai_document WHERE id = ?", String.class, ownDocument))
                .isEqualTo("1");
    }

    @Test
    void tenantAndAuditColumnsAreFilledFromTrustedContexts() {
        login(41L, 7L);
        var created = sessions.createSession(session("trusted"));
        documents.addDocument(document("trusted-document", "trusted-content"));

        assertThat(jdbc.queryForMap("SELECT user_id, create_by, tenant_id FROM ai_session WHERE id = ?", created.getId()))
                .containsEntry("USER_ID", 41L).containsEntry("CREATE_BY", 41L).containsEntry("TENANT_ID", 7L);
        assertThat(jdbc.queryForMap("SELECT user_id, create_by, tenant_id FROM ai_document WHERE title = 'trusted-document'"))
                .containsEntry("USER_ID", 41L).containsEntry("CREATE_BY", 41L).containsEntry("TENANT_ID", 7L);
    }

    @Test
    void documentIngestionCreatesBoundedChunksAndEmbeddingsBeforeItIsSearchable() {
        login(51L, 9L);
        String content = "refund policy ".repeat(80);
        documents.addDocument(document("ingested", content));
        long documentId = documentId("ingested");

        assertThat(jdbc.queryForObject("SELECT vector_status FROM ai_document WHERE id = ?", Integer.class, documentId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_embedding WHERE document_id = ? AND del_flag = '0'",
                Integer.class, documentId)).isGreaterThan(1);
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM ai_embedding WHERE document_id = ? LIMIT 1",
                String.class, documentId)).isEqualTo("bixi-local-hash-v1");
        assertThat(jdbc.queryForObject("SELECT chunk_content FROM ai_embedding WHERE document_id = ? ORDER BY chunk_index LIMIT 1",
                String.class, documentId)).contains("refund policy");

        SearchDTO query = search("policy refund", List.of(documentId));
        assertThat(documents.similaritySearch(query)).extracting("id").containsExactly(documentId);
    }

    @Test
    void failedUploadRollsBackTheDocumentAndAnyPartialEmbeddings() throws Exception {
        login(52L, 9L);
        jdbc.execute("ALTER TABLE ai_embedding ADD CONSTRAINT reject_later_chunks CHECK (chunk_index = 0)");
        MockMultipartFile upload = new MockMultipartFile(
                "file", "rollback.txt", "text/plain",
                "rollback content ".repeat(100).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> documents.uploadDocument(upload))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_embedding", Integer.class)).isZero();
    }

    @Test
    void uploadParsesPdfAndDocxBeforePersistingSearchableChunks() throws Exception {
        login(54L, 9L);

        var pdf = documents.uploadDocument(new MockMultipartFile(
                "file", "policy.pdf", "application/pdf", pdf("PDF refund policy")));
        var docx = documents.uploadDocument(new MockMultipartFile(
                "file", "guide.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("DOCX support guide")));

        assertThat(pdf.getDocType()).isEqualTo("pdf");
        assertThat(pdf.getContent()).contains("PDF refund policy");
        assertThat(docx.getDocType()).isEqualTo("docx");
        assertThat(docx.getContent()).contains("DOCX support guide");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_embedding WHERE document_id IN (?, ?)",
                Integer.class, pdf.getId(), docx.getId())).isEqualTo(2);

        assertThat(documents.similaritySearch(search("PDF refund policy", List.of(pdf.getId()))))
                .extracting("id").containsExactly(pdf.getId());
        assertThat(documents.similaritySearch(search("DOCX support guide", List.of(docx.getId()))))
                .extracting("id").containsExactly(docx.getId());
    }

    @Test
    void deletedDocumentIsExcludedFromVectorRecallAndItsChunksAreSoftDeleted() {
        login(53L, 9L);
        documents.addDocument(document("deletable", "refund policy content"));
        long documentId = documentId("deletable");
        SearchDTO query = search("refund policy", List.of(documentId));

        assertThat(documents.similaritySearch(query)).extracting("id").containsExactly(documentId);
        documents.deleteDocument(documentId);

        assertThat(documents.similaritySearch(query)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT del_flag FROM ai_embedding WHERE document_id = ?",
                String.class, documentId)).isEqualTo("1");
    }

    @Test
    void ragResponsePersistsTraceableSourcesForTheOwnedDocument() {
        login(61L, 10L);
        documents.addDocument(document("source-doc", "refund policy source text"));
        long documentId = documentId("source-doc");
        var session = sessions.createSession(session("rag"));

        MessageDTO request = message(session.getId(), "refund policy");
        request.setDocumentIds(List.of(documentId));
        var response = messages.sendRagMessage(request);

        assertThat(response.getSources()).hasSize(1);
        assertThat(response.getSources().get(0).getDocumentId()).isEqualTo(documentId);
        assertThat(response.getSources().get(0).getContent()).contains("refund policy source text");
        assertThat(jdbc.queryForObject("SELECT sources FROM ai_message WHERE id = ?", String.class, response.getId()))
                .contains("source-doc");
    }

    private void embedding(long documentId, long tenantId, String text) {
        jdbc.update("""
                INSERT INTO ai_embedding
                    (id, document_id, embedding, dimension, chunk_index, del_flag, status, data_status, tenant_id)
                VALUES (?, ?, ?, 16, 0, '0', '0', '0', ?)
                """, Math.abs(UUID.randomUUID().getMostSignificantBits()), documentId,
                VectorStoreServiceImpl.embeddingValue(text, 16), tenantId);
    }

    private long documentId(String title) {
        return jdbc.queryForObject("SELECT id FROM ai_document WHERE title = ?", Long.class, title);
    }

    private static SessionDTO session(String title) {
        SessionDTO dto = new SessionDTO();
        dto.setTitle(title);
        return dto;
    }

    private static MessageDTO message(long sessionId, String content) {
        MessageDTO dto = new MessageDTO();
        dto.setSessionId(sessionId);
        dto.setContent(content);
        return dto;
    }

    private static DocumentDTO document(String title, String content) {
        DocumentDTO dto = new DocumentDTO();
        dto.setTitle(title);
        dto.setContent(content);
        dto.setSource("test");
        dto.setDocType("txt");
        return dto;
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] docx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private static SearchDTO search(String query, List<Long> documentIds) {
        SearchDTO dto = new SearchDTO();
        dto.setQuery(query);
        dto.setDocumentIds(documentIds);
        dto.setTopK(10);
        return dto;
    }

    private static void login(long userId, long tenantId) {
        TenantContextHolder.clear();
        TenantContextHolder.set(tenantId);
        var authorities = List.of(new SimpleGrantedAuthority("ai_test"));
        var user = new BixiUser(userId, 1L, tenantId, "user-" + userId, "unused", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
        @Import({ChatServiceImpl.class, SessionServiceImpl.class, MessageServiceImpl.class, VectorStoreServiceImpl.class,
            ModelConfigServiceImpl.class,
            MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.ai.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:ai-owner-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean ChatClient chatClient() {
            return mock(ChatClient.class);
        }

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
