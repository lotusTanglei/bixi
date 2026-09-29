package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.entity.AiEmbedding;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreServiceImplTest {

    @AfterEach
    void clearSecurity() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void similaritySearchReturnsClosestDocumentFirst() {
        VectorStoreServiceImpl vectorStoreService = new VectorStoreServiceImpl(null, null);
        SearchDTO dto = new SearchDTO();
        dto.setQuery("refund policy");
        dto.setTopK(1);

        AiDocument titleMatch = new AiDocument();
        titleMatch.setId(1L);
        titleMatch.setTitle("Refund Policy");
        titleMatch.setContent("Customers can request a refund within 30 days.");
        titleMatch.setVectorStatus(0);

        AiDocument contentMatch = new AiDocument();
        contentMatch.setId(2L);
        contentMatch.setTitle("Support Guide");
        contentMatch.setContent("This guide mentions refund policy escalation.");
        contentMatch.setVectorStatus(0);

        List<DocumentVO> results = vectorStoreService.rankDocuments(List.of(contentMatch, titleMatch), dto);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Refund Policy", results.get(0).getTitle());
    }

    @Test
    void vectorSearchReturnsClosestEmbeddingFirst() {
        VectorStoreServiceImpl vectorStoreService = new VectorStoreServiceImpl(null, null);
        SearchDTO dto = new SearchDTO();
        dto.setQuery("refund policy");
        dto.setTopK(1);

        AiDocument refundPolicy = new AiDocument();
        refundPolicy.setId(1L);
        refundPolicy.setTitle("Refund Policy");

        AiDocument supportGuide = new AiDocument();
        supportGuide.setId(2L);
        supportGuide.setTitle("Support Guide");

        AiEmbedding refundEmbedding = new AiEmbedding();
        refundEmbedding.setDocumentId(1L);
        refundEmbedding.setDimension(16);
        refundEmbedding.setEmbedding(VectorStoreServiceImpl.embeddingValue("refund policy", 16));

        AiEmbedding supportEmbedding = new AiEmbedding();
        supportEmbedding.setDocumentId(2L);
        supportEmbedding.setDimension(16);
        supportEmbedding.setEmbedding(VectorStoreServiceImpl.embeddingValue("support guide", 16));

        List<DocumentVO> results = vectorStoreService.rankEmbeddingDocuments(
                List.of(supportEmbedding, refundEmbedding),
                List.of(supportGuide, refundPolicy),
                dto);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Refund Policy", results.get(0).getTitle());
    }

    @Test
    void providerEmbeddingsArePersistedAndUsedForQuerySimilarity() {
        var documentMapper = mock(com.lotus.bixi.ai.mapper.AiDocumentMapper.class);
        var embeddingMapper = mock(com.lotus.bixi.ai.mapper.AiEmbeddingMapper.class);
        int[] batchCalls = {0};
        int[] queryCalls = {0};
        EmbeddingModel provider = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                throw new UnsupportedOperationException("call is not used by this contract test");
            }

            @Override
            public float[] embed(Document document) {
                return new float[] {0.0f, 1.0f, 0.0f};
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                batchCalls[0]++;
                return texts.stream()
                        .map(text -> text.startsWith("first")
                                ? new float[] {1.0f, 0.0f, 0.0f}
                                : new float[] {0.0f, 1.0f, 0.0f})
                        .toList();
            }

            @Override
            public float[] embed(String text) {
                queryCalls[0]++;
                return new float[] {0.0f, 1.0f, 0.0f};
            }
        };
        var insertedEmbeddings = new ArrayList<AiEmbedding>();
        when(documentMapper.insert(any(AiDocument.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AiDocument.class).setId(7L);
            return 1;
        });
        when(documentMapper.updateById(any(AiDocument.class))).thenReturn(1);
        when(embeddingMapper.insert(any(AiEmbedding.class))).thenAnswer(invocation -> {
            insertedEmbeddings.add(invocation.getArgument(0, AiEmbedding.class));
            return 1;
        });
        TenantContextHolder.set(9L);
        var authorities = List.of(new SimpleGrantedAuthority("ai_document_add"));
        var user = new BixiUser(11L, 1L, 9L, "provider-test", "unused", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));

        VectorStoreServiceImpl service = new VectorStoreServiceImpl(
                documentMapper, embeddingMapper, provider, "dashscope-test-model");
        DocumentDTO dto = new DocumentDTO();
        dto.setTitle("provider document");
        dto.setContent("first chunk ".repeat(45) + "\n\nsecond chunk ".repeat(35));
        dto.setSource("test");
        dto.setDocType("txt");

        service.addDocument(dto);

        assertEquals(2, insertedEmbeddings.size());
        assertEquals("dashscope-test-model", insertedEmbeddings.get(0).getEmbeddingModel());
        assertEquals(3, insertedEmbeddings.get(0).getDimension());
        assertEquals("[1.0,0.0,0.0]", insertedEmbeddings.get(0).getEmbedding());
        assertEquals(1, batchCalls[0]);

        SearchDTO search = new SearchDTO();
        search.setQuery("provider query");
        search.setTopK(1);
        AiDocument persisted = new AiDocument();
        persisted.setId(7L);
        persisted.setTitle("provider document");
        persisted.setTenantId(9L);
        persisted.setUserId(11L);
        when(documentMapper.selectList(any())).thenReturn(List.of(persisted));
        when(embeddingMapper.selectList(any())).thenReturn(List.of(insertedEmbeddings.get(0), insertedEmbeddings.get(1)));

        assertEquals(7L, service.similaritySearch(search).get(0).getId());
        assertEquals(1, queryCalls[0]);
    }

    @Test
    void rejectsInvalidSearchBoundsAndNonFiniteStoredVectors() {
        VectorStoreServiceImpl service = new VectorStoreServiceImpl(null, null);
        AiDocument document = new AiDocument();
        document.setId(1L);
        document.setTitle("document");

        SearchDTO invalidTopK = new SearchDTO();
        invalidTopK.setTopK(0);
        assertThatThrownBy(() -> service.rankDocuments(List.of(document), invalidTopK))
                .isInstanceOf(IllegalArgumentException.class);

        SearchDTO invalidThreshold = new SearchDTO();
        invalidThreshold.setThreshold(Double.NaN);
        assertThatThrownBy(() -> service.rankDocuments(List.of(document), invalidThreshold))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(service.parseVector("[NaN, 1.0]")).isEmpty();
        assertThat(service.parseVector("[Infinity, 1.0]")).isEmpty();
    }

    @Test
    void failedDocumentInsertStopsBeforeEmbeddingPersistence() {
        var documentMapper = mock(com.lotus.bixi.ai.mapper.AiDocumentMapper.class);
        var embeddingMapper = mock(com.lotus.bixi.ai.mapper.AiEmbeddingMapper.class);
        when(documentMapper.insert(any(AiDocument.class))).thenReturn(0);
        authenticate(19L, 9L);

        DocumentDTO dto = new DocumentDTO();
        dto.setTitle("insert failure");
        dto.setContent("content");

        VectorStoreServiceImpl service = new VectorStoreServiceImpl(documentMapper, embeddingMapper);
        assertThatThrownBy(() -> service.addDocument(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI文档保存失败");
        verify(embeddingMapper, never()).insert(any(AiEmbedding.class));
    }

    @Test
    void oversizedUploadIsRejectedBeforeReadingTheMultipartBody() throws Exception {
        authenticate(20L, 9L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) DocumentContentExtractor.MAX_DOCUMENT_BYTES + 1);
        when(file.getOriginalFilename()).thenReturn("oversized.txt");
        when(file.getBytes()).thenThrow(new AssertionError("multipart body must not be read"));

        VectorStoreServiceImpl service = new VectorStoreServiceImpl(null, null);

        assertThatThrownBy(() -> service.uploadDocument(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI document is too large");
        verify(file, never()).getBytes();
    }

    @Test
    void oversizedJsonDocumentIsRejectedBeforePersistence() {
        authenticate(21L, 9L);
        var documentMapper = mock(com.lotus.bixi.ai.mapper.AiDocumentMapper.class);
        var embeddingMapper = mock(com.lotus.bixi.ai.mapper.AiEmbeddingMapper.class);

        DocumentDTO dto = new DocumentDTO();
        dto.setTitle("oversized JSON document");
        // Three-byte UTF-8 characters prove the bound is measured in bytes,
        // matching the multipart ingestion limit.
        dto.setContent("中".repeat(DocumentContentExtractor.MAX_DOCUMENT_BYTES / 3 + 1));

        VectorStoreServiceImpl service = new VectorStoreServiceImpl(documentMapper, embeddingMapper);

        assertThatThrownBy(() -> service.addDocument(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI document is too large");
        verify(documentMapper, never()).insert(any(AiDocument.class));
    }

    private static void authenticate(long userId, long tenantId) {
        TenantContextHolder.set(tenantId);
        var authority = new SimpleGrantedAuthority("ai_document_add");
        var user = new BixiUser(userId, 1L, tenantId, "vector-test", "unused", null,
                true, true, true, true, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority)));
    }
}
