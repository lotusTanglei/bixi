package com.lotus.bixi.ai.service;

import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.vo.DocumentVO;

import java.util.List;

/**
 * 向量存储服务接口
 *
 * @author bixi
 * @date 2025-01-01
 */
public interface VectorStoreService {

    void addDocument(DocumentDTO dto);

    void addDocuments(List<DocumentDTO> dtos);

    List<DocumentVO> similaritySearch(SearchDTO dto);

    void deleteDocument(Long documentId);
}
