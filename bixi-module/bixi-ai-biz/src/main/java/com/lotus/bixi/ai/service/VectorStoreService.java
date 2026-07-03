package com.lotus.bixi.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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

    IPage<DocumentVO> pageDocuments(Page<AiDocument> page, String title);

    List<DocumentVO> listDocuments(String title);

    DocumentVO uploadDocument(MultipartFile file) throws IOException;

    void deleteDocument(Long documentId);
}
