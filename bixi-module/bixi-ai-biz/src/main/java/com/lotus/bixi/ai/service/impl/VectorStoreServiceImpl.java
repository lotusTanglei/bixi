package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.entity.AiEmbedding;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.mapper.AiDocumentMapper;
import com.lotus.bixi.ai.mapper.AiEmbeddingMapper;
import com.lotus.bixi.ai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final AiEmbeddingMapper embeddingMapper;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 64;
    private static final Pattern VECTOR_SPLITTER = Pattern.compile("\\s*,\\s*");

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
        if (StringUtils.hasText(query)) {
            List<DocumentVO> vectorResults = vectorSimilaritySearch(dto, query);
            if (!vectorResults.isEmpty()) {
                return vectorResults;
            }
        }

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

    private List<DocumentVO> vectorSimilaritySearch(SearchDTO dto, String query) {
        LambdaQueryWrapper<AiEmbedding> embeddingWrapper = new LambdaQueryWrapper<AiEmbedding>()
                .eq(AiEmbedding::getDelFlag, "0")
                .isNotNull(AiEmbedding::getEmbedding);
        if (dto != null && dto.getDocumentIds() != null && !dto.getDocumentIds().isEmpty()) {
            embeddingWrapper.in(AiEmbedding::getDocumentId, dto.getDocumentIds());
        }

        List<AiEmbedding> embeddings = embeddingMapper.selectList(embeddingWrapper);
        if (embeddings.isEmpty()) {
            return List.of();
        }

        List<Long> documentIds = embeddings.stream()
                .map(AiEmbedding::getDocumentId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return List.of();
        }

        List<AiDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .in(AiDocument::getId, documentIds)
                .eq(AiDocument::getDelFlag, "0"));

        return rankEmbeddingDocuments(embeddings, documents, dto);
    }

    List<DocumentVO> rankEmbeddingDocuments(List<AiEmbedding> embeddings, List<AiDocument> documents, SearchDTO dto) {
        if (embeddings == null || embeddings.isEmpty() || documents == null || documents.isEmpty()) {
            return List.of();
        }

        String query = dto == null ? null : dto.getQuery();
        int topK = dto != null && dto.getTopK() != null ? dto.getTopK() : 5;
        Double threshold = dto == null ? null : dto.getThreshold();
        int dimension = embeddings.stream()
                .map(AiEmbedding::getDimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(DEFAULT_EMBEDDING_DIMENSION);
        double[] queryVector = textEmbedding(query, dimension);

        Map<Long, AiDocument> documentById = documents.stream()
                .filter(document -> document.getId() != null)
                .collect(Collectors.toMap(AiDocument::getId, document -> document, (left, right) -> left));
        Map<Long, Double> scoreByDocumentId = new HashMap<>();

        for (AiEmbedding embedding : embeddings) {
            AiDocument document = documentById.get(embedding.getDocumentId());
            if (document == null) {
                continue;
            }
            parseVector(embedding.getEmbedding())
                    .map(vector -> cosineSimilarity(queryVector, vector))
                    .ifPresent(score -> scoreByDocumentId.merge(document.getId(), score, Math::max));
        }

        return scoreByDocumentId.entrySet().stream()
                .filter(entry -> threshold == null || entry.getValue() >= threshold)
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> toDocumentVO(documentById.get(entry.getKey()), entry.getValue()))
                .toList();
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

    Optional<double[]> parseVector(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            double[] vector = Arrays.stream(VECTOR_SPLITTER.split(normalized))
                    .filter(StringUtils::hasText)
                    .mapToDouble(Double::parseDouble)
                    .toArray();
            return vector.length == 0 ? Optional.empty() : Optional.of(vector);
        }
        catch (NumberFormatException ex) {
            log.warn("忽略无法解析的向量数据: {}", value);
            return Optional.empty();
        }
    }

    static String embeddingValue(String text, int dimension) {
        return Arrays.stream(textEmbedding(text, dimension))
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static double[] textEmbedding(String text, int dimension) {
        int vectorDimension = dimension > 0 ? dimension : DEFAULT_EMBEDDING_DIMENSION;
        double[] vector = new double[vectorDimension];
        if (!StringUtils.hasText(text)) {
            return vector;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+")) {
            if (!token.isBlank()) {
                vector[Math.floorMod(token.hashCode(), vectorDimension)] += 1.0;
            }
        }
        return vector;
    }

    private double cosineSimilarity(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0.0 || rightNorm == 0.0 ? 0.0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String resolveDocType(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "unknown";
        }
        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
