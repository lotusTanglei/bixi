package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.mapper.AiDocumentMapper;
import com.lotus.bixi.ai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量存储服务实现
 *
 * @author bixi
 * @date 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final AiDocumentMapper documentMapper;

    @Override
    public void addDocument(DocumentDTO dto) {
        AiDocument document = new AiDocument();
        document.setTitle(dto.getTitle());
        document.setContent(dto.getContent());
        document.setSource(dto.getSource());
        document.setDocType(dto.getDocType());
        document.setVectorStatus(0);
        documentMapper.insert(document);
    }

    @Override
    public void addDocuments(List<DocumentDTO> dtos) {
        for (DocumentDTO dto : dtos) {
            addDocument(dto);
        }
    }

    @Override
    public List<DocumentVO> similaritySearch(SearchDTO dto) {
        return new ArrayList<>();
    }

    @Override
    public void deleteDocument(Long documentId) {
        documentMapper.deleteById(documentId);
    }
}
