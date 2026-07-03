package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VectorStoreServiceImplTest {

    @Test
    void similaritySearchReturnsClosestDocumentFirst() {
        VectorStoreServiceImpl vectorStoreService = new VectorStoreServiceImpl(null);
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
}
