package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.mapper.AiDocumentMapper;
import com.lotus.bixi.ai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
        documentMapper.insert(toDocument(dto));
    }

    @Override
    public void addDocuments(List<DocumentDTO> dtos) {
        for (DocumentDTO dto : dtos) {
            addDocument(dto);
        }
    }

    @Override
    public List<DocumentVO> similaritySearch(SearchDTO dto) {
        String query = dto == null ? null : dto.getQuery();

        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getDelFlag, "0");
        if (dto != null && dto.getDocumentIds() != null && !dto.getDocumentIds().isEmpty()) {
            wrapper.in(AiDocument::getId, dto.getDocumentIds());
        }
        if (StringUtils.hasText(query)) {
            wrapper.and(condition -> condition
                    .like(AiDocument::getTitle, query)
                    .or()
                    .like(AiDocument::getContent, query)
                    .or()
                    .like(AiDocument::getSource, query));
        }

        return rankDocuments(documentMapper.selectList(wrapper), dto);
    }

    List<DocumentVO> rankDocuments(List<AiDocument> documents, SearchDTO dto) {
        String query = dto == null ? null : dto.getQuery();
        int topK = dto != null && dto.getTopK() != null ? dto.getTopK() : 5;
        Double threshold = dto == null ? null : dto.getThreshold();

        return documents.stream()
                .map(document -> toDocumentVO(document, score(document, query)))
                .filter(vo -> threshold == null || vo.getScore() >= threshold)
                .sorted(Comparator.comparing(DocumentVO::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public IPage<DocumentVO> pageDocuments(Page<AiDocument> page, String title) {
        IPage<AiDocument> documentPage = documentMapper.selectPage(page, buildDocumentQuery(title));
        Page<DocumentVO> voPage = new Page<>(documentPage.getCurrent(), documentPage.getSize(), documentPage.getTotal());
        voPage.setRecords(documentPage.getRecords().stream()
                .map(document -> toDocumentVO(document, null))
                .toList());
        return voPage;
    }

    @Override
    public List<DocumentVO> listDocuments(String title) {
        return documentMapper.selectList(buildDocumentQuery(title)).stream()
                .map(document -> toDocumentVO(document, null))
                .toList();
    }

    @Override
    public DocumentVO uploadDocument(MultipartFile file) throws IOException {
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "uploaded-document");
        DocumentDTO dto = new DocumentDTO();
        dto.setTitle(filename);
        dto.setContent(new String(file.getBytes(), StandardCharsets.UTF_8));
        dto.setSource(filename);
        dto.setDocType(resolveDocType(filename));
        AiDocument document = toDocument(dto);
        documentMapper.insert(document);
        return toDocumentVO(document, null);
    }

    @Override
    public void deleteDocument(Long documentId) {
        documentMapper.deleteById(documentId);
    }

    private LambdaQueryWrapper<AiDocument> buildDocumentQuery(String title) {
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getDelFlag, "0")
                .orderByDesc(AiDocument::getCreateTime);
        if (StringUtils.hasText(title)) {
            wrapper.and(condition -> condition
                    .like(AiDocument::getTitle, title)
                    .or()
                    .like(AiDocument::getSource, title));
        }
        return wrapper;
    }

    private AiDocument toDocument(DocumentDTO dto) {
        AiDocument document = new AiDocument();
        document.setTitle(dto.getTitle());
        document.setContent(dto.getContent());
        document.setSource(dto.getSource());
        document.setDocType(dto.getDocType());
        document.setVectorStatus(0);
        return document;
    }

    private DocumentVO toDocumentVO(AiDocument document, Double score) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(document, vo);
        vo.setScore(score);
        return vo;
    }

    private Double score(AiDocument document, String query) {
        if (!StringUtils.hasText(query)) {
            return 0.0;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        double score = 0.0;
        score += contains(document.getTitle(), normalizedQuery) ? 0.5 : 0.0;
        score += contains(document.getContent(), normalizedQuery) ? 0.4 : 0.0;
        score += contains(document.getSource(), normalizedQuery) ? 0.1 : 0.0;
        return score;
    }

    private boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private String resolveDocType(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "unknown";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
