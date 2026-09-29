package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.api.dto.SearchDTO;
import com.lotus.bixi.ai.api.entity.AiDocument;
import com.lotus.bixi.ai.api.entity.AiEmbedding;
import com.lotus.bixi.ai.api.vo.DocumentVO;
import com.lotus.bixi.ai.mapper.AiDocumentMapper;
import com.lotus.bixi.ai.mapper.AiEmbeddingMapper;
import com.lotus.bixi.ai.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.stream.IntStream;

/**
 * 向量存储服务实现
 *
 * @author bixi
 * @date 2025-01-01
 */
@Slf4j
@Service
@ConditionalOnAiEnabled
public class VectorStoreServiceImpl implements VectorStoreService {

    private final AiDocumentMapper documentMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final EmbeddingModel embeddingModel;
    private final String embeddingModelName;
    private static final int DEFAULT_EMBEDDING_DIMENSION = 64;
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 120;
    private static final String LOCAL_EMBEDDING_MODEL = "bixi-local-hash-v1";
    private static final Pattern VECTOR_SPLITTER = Pattern.compile("\\s*,\\s*");

    @Autowired
    VectorStoreServiceImpl(AiDocumentMapper documentMapper,
                           AiEmbeddingMapper embeddingMapper,
                           ObjectProvider<EmbeddingModel> embeddingModels,
                           @Value("${spring.ai.dashscope.embedding.options.model:text-embedding-v2}")
                           String embeddingModelName) {
        this(documentMapper, embeddingMapper, embeddingModels.getIfAvailable(), embeddingModelName);
    }

    VectorStoreServiceImpl(AiDocumentMapper documentMapper,
                           AiEmbeddingMapper embeddingMapper) {
        this(documentMapper, embeddingMapper, (EmbeddingModel) null, LOCAL_EMBEDDING_MODEL);
    }

    VectorStoreServiceImpl(AiDocumentMapper documentMapper,
                           AiEmbeddingMapper embeddingMapper,
                           EmbeddingModel embeddingModel,
                           String embeddingModelName) {
        this.documentMapper = documentMapper;
        this.embeddingMapper = embeddingMapper;
        this.embeddingModel = embeddingModel;
        this.embeddingModelName = StringUtils.hasText(embeddingModelName)
                ? embeddingModelName.trim() : LOCAL_EMBEDDING_MODEL;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addDocument(DocumentDTO dto) {
        AiOwnershipSupport.requireWritable();
        if (dto == null) {
            throw new IllegalArgumentException("Document is required");
        }
        validateDocument(dto);
        AiDocument document = toDocument(dto, AiOwnershipSupport.requireUser());
        if (documentMapper.insert(document) <= 0 || document.getId() == null) {
            throw new IllegalStateException("AI文档保存失败");
        }
        ingestEmbeddings(document);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addDocuments(List<DocumentDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("At least one document is required");
        }
        for (DocumentDTO dto : dtos) {
            addDocument(dto);
        }
    }

    @Override
    public List<DocumentVO> similaritySearch(SearchDTO dto) {
        var user = AiOwnershipSupport.requireUser();
        Long tenantId = AiOwnershipSupport.tenantId();
        String query = dto == null ? null : dto.getQuery();
        if (StringUtils.hasText(query)) {
            List<DocumentVO> vectorResults = vectorSimilaritySearch(dto, query, user.getId(), tenantId);
            if (!vectorResults.isEmpty()) {
                return vectorResults;
            }
        }

        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getDelFlag, "0")
                .eq(AiDocument::getUserId, user.getId())
                .eq(AiDocument::getTenantId, tenantId);
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

    private List<DocumentVO> vectorSimilaritySearch(SearchDTO dto, String query, Long userId, Long tenantId) {
        List<AiDocument> ownedDocuments = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getDelFlag, "0")
                .eq(AiDocument::getUserId, userId)
                .eq(AiDocument::getTenantId, tenantId)
                .in(dto != null && dto.getDocumentIds() != null && !dto.getDocumentIds().isEmpty(),
                        AiDocument::getId, dto == null ? List.of() : dto.getDocumentIds()));
        if (ownedDocuments.isEmpty()) {
            return List.of();
        }
        List<Long> ownedDocumentIds = ownedDocuments.stream().map(AiDocument::getId).toList();
        LambdaQueryWrapper<AiEmbedding> embeddingWrapper = new LambdaQueryWrapper<AiEmbedding>()
                .eq(AiEmbedding::getDelFlag, "0")
                .eq(AiEmbedding::getTenantId, tenantId)
                .isNotNull(AiEmbedding::getEmbedding)
                .in(AiEmbedding::getDocumentId, ownedDocumentIds);

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

        List<AiDocument> documents = ownedDocuments.stream()
                .filter(document -> documentIds.contains(document.getId()))
                .toList();

        return rankEmbeddingDocuments(embeddings, documents, dto);
    }

    List<DocumentVO> rankEmbeddingDocuments(List<AiEmbedding> embeddings, List<AiDocument> documents, SearchDTO dto) {
        if (embeddings == null || embeddings.isEmpty() || documents == null || documents.isEmpty()) {
            return List.of();
        }

        String query = dto == null ? null : dto.getQuery();
        int topK = topK(dto);
        Double threshold = threshold(dto);
        int dimension = embeddings.stream()
                .map(AiEmbedding::getDimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(DEFAULT_EMBEDDING_DIMENSION);
        double[] queryVector = queryEmbedding(query, dimension);

        Map<Long, AiDocument> documentById = documents.stream()
                .filter(document -> document.getId() != null)
                .collect(Collectors.toMap(AiDocument::getId, document -> document, (left, right) -> left));
        Map<Long, EmbeddingHit> bestHitByDocumentId = new HashMap<>();

        for (AiEmbedding embedding : embeddings) {
            AiDocument document = documentById.get(embedding.getDocumentId());
            if (document == null) {
                continue;
            }
            parseVector(embedding.getEmbedding())
                    .filter(vector -> vector.length == dimension)
                    .map(vector -> cosineSimilarity(queryVector, vector))
                    .ifPresent(score -> bestHitByDocumentId.merge(document.getId(),
                            new EmbeddingHit(score, embedding.getChunkIndex(), embedding.getChunkContent()),
                            (left, right) -> left.score() >= right.score() ? left : right));
        }

        return bestHitByDocumentId.entrySet().stream()
                .filter(entry -> threshold == null || entry.getValue().score() >= threshold)
                .sorted(Map.Entry.<Long, EmbeddingHit>comparingByValue(
                        Comparator.comparingDouble(EmbeddingHit::score)).reversed())
                .limit(topK)
                .map(entry -> toDocumentVO(documentById.get(entry.getKey()), entry.getValue().score(),
                        entry.getValue().chunkIndex(), entry.getValue().snippet()))
                .toList();
    }

    List<DocumentVO> rankDocuments(List<AiDocument> documents, SearchDTO dto) {
        String query = dto == null ? null : dto.getQuery();
        int topK = topK(dto);
        Double threshold = threshold(dto);

        return documents.stream()
                .map(document -> toDocumentVO(document, score(document, query)))
                .filter(vo -> threshold == null || vo.getScore() >= threshold)
                .sorted(Comparator.comparing(DocumentVO::getScore, Comparator.nullsLast(Double::compareTo)).reversed())
                .limit(topK)
                .toList();
    }

    @Override
    public IPage<DocumentVO> pageDocuments(Page<AiDocument> page, String title) {
        var user = AiOwnershipSupport.requireUser();
        IPage<AiDocument> documentPage = documentMapper.selectPage(page,
                buildDocumentQuery(title, user.getId(), AiOwnershipSupport.tenantId()));
        Page<DocumentVO> voPage = new Page<>(documentPage.getCurrent(), documentPage.getSize(), documentPage.getTotal());
        voPage.setRecords(documentPage.getRecords().stream()
                .map(document -> toDocumentVO(document, null))
                .toList());
        return voPage;
    }

    @Override
    public List<DocumentVO> listDocuments(String title) {
        var user = AiOwnershipSupport.requireUser();
        return documentMapper.selectList(buildDocumentQuery(title, user.getId(), AiOwnershipSupport.tenantId())).stream()
                .map(document -> toDocumentVO(document, null))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO uploadDocument(MultipartFile file) throws IOException {
        AiOwnershipSupport.requireWritable();
        var user = AiOwnershipSupport.requireUser();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Document file is empty");
        }
        long declaredSize = file.getSize();
        if (declaredSize > DocumentContentExtractor.MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("AI document is too large");
        }
        String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "uploaded-document");
        DocumentContentExtractor.ExtractedContent extracted =
                DocumentContentExtractor.extract(filename, file.getBytes());
        DocumentDTO dto = new DocumentDTO();
        dto.setTitle(filename);
        dto.setContent(extracted.text());
        dto.setSource(filename);
        dto.setDocType(resolveDocType(filename));
        AiDocument document = toDocument(dto, user, AiOwnershipSupport.tenantId());
        if (documentMapper.insert(document) <= 0 || document.getId() == null) {
            throw new IllegalStateException("AI文档保存失败");
        }
        ingestEmbeddings(document);
        return toDocumentVO(document, null);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        AiOwnershipSupport.requireWritable();
        var user = AiOwnershipSupport.requireUser();
        Long tenantId = AiOwnershipSupport.tenantId();
        AiDocument document = documentMapper.selectOne(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getId, documentId)
                .eq(AiDocument::getUserId, user.getId())
                .eq(AiDocument::getTenantId, tenantId)
                .eq(AiDocument::getDelFlag, "0"));
        if (document == null) {
            throw AiOwnershipSupport.missing("AI文档");
        }
        embeddingMapper.delete(new LambdaQueryWrapper<AiEmbedding>()
                .eq(AiEmbedding::getDocumentId, documentId)
                .eq(AiEmbedding::getTenantId, tenantId));
        documentMapper.delete(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getId, documentId)
                .eq(AiDocument::getUserId, user.getId())
                .eq(AiDocument::getTenantId, tenantId));
    }

    private LambdaQueryWrapper<AiDocument> buildDocumentQuery(String title, Long userId, Long tenantId) {
        LambdaQueryWrapper<AiDocument> wrapper = new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getDelFlag, "0")
                .eq(AiDocument::getUserId, userId)
                .eq(AiDocument::getTenantId, tenantId)
                .orderByDesc(AiDocument::getCreateTime);
        if (StringUtils.hasText(title)) {
            wrapper.and(condition -> condition
                    .like(AiDocument::getTitle, title)
                    .or()
                    .like(AiDocument::getSource, title));
        }
        return wrapper;
    }

    private AiDocument toDocument(DocumentDTO dto, com.lotus.bixi.common.security.service.BixiUser user) {
        return toDocument(dto, user, AiOwnershipSupport.tenantId());
    }

    private AiDocument toDocument(DocumentDTO dto,
                                  com.lotus.bixi.common.security.service.BixiUser user,
                                  Long tenantId) {
        AiDocument document = new AiDocument();
        document.setTitle(dto.getTitle());
        document.setContent(dto.getContent());
        document.setSource(dto.getSource());
        document.setDocType(dto.getDocType());
        document.setVectorStatus(0);
        document.setUserId(user.getId());
        document.setTenantId(tenantId);
        return document;
    }

    private void ingestEmbeddings(AiDocument document) {
        List<String> chunks = DocumentContentExtractor.chunk(document.getContent(), CHUNK_SIZE, CHUNK_OVERLAP);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document contains no readable text");
        }
        List<float[]> vectors = embedChunks(chunks);
        int dimension = validateDimensions(vectors);
        String model = embeddingModel == null ? LOCAL_EMBEDDING_MODEL : embeddingModelName;
        Long tenantId = document.getTenantId() != null ? document.getTenantId() : AiOwnershipSupport.tenantId();
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index);
            AiEmbedding embedding = new AiEmbedding();
            embedding.setDocumentId(document.getId());
            embedding.setVectorId(document.getId() + ":" + index);
            embedding.setEmbeddingModel(model);
            embedding.setDimension(dimension);
            embedding.setChunkIndex(index);
            embedding.setChunkContent(chunk);
            embedding.setEmbedding(embeddingModel == null
                    ? embeddingValue(chunk, DEFAULT_EMBEDDING_DIMENSION)
                    : embeddingValue(vectors.get(index)));
            embedding.setTenantId(tenantId);
            if (embeddingMapper.insert(embedding) <= 0) {
                throw new IllegalStateException("AI向量保存失败");
            }
        }
        document.setVectorStatus(1);
        if (documentMapper.updateById(document) <= 0) {
            throw new IllegalStateException("AI文档向量状态更新失败");
        }
    }

    private void validateDocument(DocumentDTO dto) {
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        if (!StringUtils.hasText(dto.getContent())) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        // Multipart uploads are bounded before reading their body. Apply the
        // same bound to JSON ingestion so callers cannot bypass the parser
        // guard with an oversized in-memory request.
        if (dto.getContent().getBytes(StandardCharsets.UTF_8).length
                > DocumentContentExtractor.MAX_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("AI document is too large");
        }
    }

    private List<float[]> embedChunks(List<String> chunks) {
        if (embeddingModel == null) {
            return chunks.stream()
                    .map(chunk -> toFloatVector(textEmbedding(chunk, DEFAULT_EMBEDDING_DIMENSION)))
                    .toList();
        }
        final List<float[]> vectors;
        try {
            vectors = embeddingModel.embed(chunks);
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("AI embedding provider failed", ex);
        }
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new IllegalStateException("AI embedding provider returned an unexpected vector count");
        }
        return vectors;
    }

    private int validateDimensions(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty() || vectors.get(0) == null || vectors.get(0).length == 0) {
            throw new IllegalStateException("AI embedding provider returned an empty vector");
        }
        int dimension = vectors.get(0).length;
        if (vectors.stream().anyMatch(vector -> vector == null || vector.length != dimension || vector.length == 0
                || containsNonFinite(vector))) {
            throw new IllegalStateException("AI embedding provider returned inconsistent vector dimensions");
        }
        return dimension;
    }

    private double[] queryEmbedding(String query, int expectedDimension) {
        if (embeddingModel == null) {
            return textEmbedding(query, expectedDimension);
        }
        final float[] vector;
        try {
            vector = embeddingModel.embed(query);
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("AI embedding provider failed", ex);
        }
        if (vector == null || vector.length == 0 || vector.length != expectedDimension || containsNonFinite(vector)) {
            throw new IllegalStateException("AI embedding provider returned an incompatible query dimension");
        }
        return toDoubleVector(vector);
    }

    private DocumentVO toDocumentVO(AiDocument document, Double score) {
        return toDocumentVO(document, score, null, null);
    }

    private DocumentVO toDocumentVO(AiDocument document, Double score, Integer chunkIndex, String snippet) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(document, vo);
        vo.setScore(score);
        vo.setChunkIndex(chunkIndex);
        vo.setSnippet(snippet);
        return vo;
    }

    private record EmbeddingHit(double score, Integer chunkIndex, String snippet) {
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
        if (normalized.startsWith("[") != normalized.endsWith("]")) {
            return Optional.empty();
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            double[] vector = Arrays.stream(VECTOR_SPLITTER.split(normalized))
                    .filter(StringUtils::hasText)
                    .mapToDouble(Double::parseDouble)
                    .toArray();
            return vector.length == 0 || containsNonFinite(vector) ? Optional.empty() : Optional.of(vector);
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

    static String embeddingValue(float[] vector) {
        if (vector == null || vector.length == 0 || containsNonFinite(vector)) {
            throw new IllegalArgumentException("Embedding vector must not be empty");
        }
        return IntStream.range(0, vector.length)
                .mapToObj(index -> Float.toString(vector[index]))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static float[] toFloatVector(double[] vector) {
        float[] result = new float[vector.length];
        for (int index = 0; index < vector.length; index++) {
            result[index] = (float) vector[index];
        }
        return result;
    }

    private static double[] toDoubleVector(float[] vector) {
        double[] result = new double[vector.length];
        for (int index = 0; index < vector.length; index++) {
            result[index] = vector[index];
        }
        return result;
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
        if (left == null || right == null || left.length == 0 || left.length != right.length
                || containsNonFinite(left) || containsNonFinite(right)) {
            return 0.0;
        }
        int length = left.length;
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

    private int topK(SearchDTO dto) {
        int value = dto == null || dto.getTopK() == null ? 5 : dto.getTopK();
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("topK必须在1到100之间");
        }
        return value;
    }

    private Double threshold(SearchDTO dto) {
        Double value = dto == null ? null : dto.getThreshold();
        if (value != null && (!Double.isFinite(value) || value < -1 || value > 1)) {
            throw new IllegalArgumentException("相似度阈值必须在-1到1之间");
        }
        return value;
    }

    private static boolean containsNonFinite(float[] vector) {
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsNonFinite(double[] vector) {
        for (double value : vector) {
            if (!Double.isFinite(value)) {
                return true;
            }
        }
        return false;
    }
}
