package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.entity.AiModelConfig;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.mapper.AiModelConfigMapper;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Service
@ConditionalOnAiEnabled
public class ModelConfigServiceImpl implements ModelConfigService {

    private static final ConfigState DEFAULT_CONFIG = new ConfigState(
            AiConstants.DEFAULT_MODEL, 0.7, 2000, 0.9, "");

    private static final Set<String> AVAILABLE_MODEL_IDS = Set.of(
            "qwen-turbo", "qwen-plus", "qwen-max", "qwen-long");

    private final AiModelConfigMapper mapper;

    @Autowired
    public ModelConfigServiceImpl(AiModelConfigMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public ModelConfigVO getConfig() {
        Long tenantId = AiOwnershipSupport.tenantId();
        ConfigState config = withTenantContext(tenantId, () -> fromEntity(mapper.selectByTenantId(tenantId)));
        ModelConfigVO vo = new ModelConfigVO();
        vo.setCurrentModel(config.currentModel());
        vo.setTemperature(config.temperature());
        vo.setMaxTokens(config.maxTokens());
        vo.setTopP(config.topP());
        vo.setSystemPrompt(config.systemPrompt());
        vo.setAvailableModels(listModels());
        return vo;
    }

    @Override
    public List<ModelConfigVO.ModelInfo> listModels() {
        AiOwnershipSupport.requireUser();
        return Arrays.asList(
                createModelInfo("qwen-turbo", "通义千问-Turbo", "快速响应，适合简单对话"),
                createModelInfo("qwen-plus", "通义千问-Plus", "平衡性能，适合日常使用"),
                createModelInfo("qwen-max", "通义千问-Max", "最强能力，适合复杂任务"),
                createModelInfo("qwen-long", "通义千问-Long", "超长上下文，适合长文档处理")
        );
    }

    @Override
    public void updateConfig(ModelConfigDTO dto) {
        AiOwnershipSupport.requireWritable();
        if (dto == null) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        Long tenantId = AiOwnershipSupport.tenantId();
        ConfigState update = merge(dto, DEFAULT_CONFIG);
        BixiUser user = AiOwnershipSupport.requireUser();
        withTenantContext(tenantId, () -> {
            // This update is deliberately field-wise and atomic. It avoids a
            // stale read from one cloud instance overwriting another instance's
            // concurrent partial update.
            int updated = mapper.updateValues(tenantId,
                    normalizeModel(dto.getModel()), dto.getTemperature() == null ? null : clampTemperature(dto.getTemperature()),
                    dto.getMaxTokens() == null ? null : clampMaxTokens(dto.getMaxTokens()),
                    dto.getTopP() == null ? null : clampTopP(dto.getTopP()),
                    dto.getSystemPrompt(), user.getId());
            if (updated > 0) {
                return null;
            }

            // MySQL reports zero affected rows when all supplied values already
            // equal the stored values. Confirm existence before attempting the
            // first insert; otherwise a harmless retry becomes a duplicate-key
            // failure. This read also keeps the operation correct when a JDBC
            // driver is configured with CLIENT_FOUND_ROWS disabled.
            if (mapper.selectByTenantId(tenantId) != null) {
                return null;
            }

            AiModelConfig initial = toEntity(tenantId, user.getId(), update);
            try {
                mapper.insert(initial);
            }
            catch (DuplicateKeyException collision) {
                // Another instance won the first insert. Retry the atomic
                // partial update against that row rather than failing or
                // replacing fields it may have written.
                int retried = mapper.updateValues(tenantId,
                        normalizeModel(dto.getModel()), dto.getTemperature() == null ? null : clampTemperature(dto.getTemperature()),
                        dto.getMaxTokens() == null ? null : clampMaxTokens(dto.getMaxTokens()),
                        dto.getTopP() == null ? null : clampTopP(dto.getTopP()),
                        dto.getSystemPrompt(), user.getId());
                if (retried == 0 && mapper.selectByTenantId(tenantId) == null) {
                    throw collision;
                }
            }
            return null;
        });
    }

    private ConfigState fromEntity(AiModelConfig entity) {
        if (entity == null) {
            return DEFAULT_CONFIG;
        }
        return new ConfigState(
                valueOr(entity.getCurrentModel(), DEFAULT_CONFIG.currentModel()),
                valueOr(entity.getTemperature(), DEFAULT_CONFIG.temperature()),
                valueOr(entity.getMaxTokens(), DEFAULT_CONFIG.maxTokens()),
                valueOr(entity.getTopP(), DEFAULT_CONFIG.topP()),
                valueOr(entity.getSystemPrompt(), DEFAULT_CONFIG.systemPrompt()));
    }

    private AiModelConfig toEntity(Long tenantId, Long userId, ConfigState state) {
        AiModelConfig entity = new AiModelConfig();
        entity.setTenantId(tenantId);
        entity.setCreateBy(userId);
        entity.setCurrentModel(state.currentModel());
        entity.setTemperature(state.temperature());
        entity.setMaxTokens(state.maxTokens());
        entity.setTopP(state.topP());
        entity.setSystemPrompt(state.systemPrompt());
        entity.setDelFlag("0");
        entity.setStatus("0");
        entity.setDataStatus("0");
        return entity;
    }

    private ConfigState merge(ModelConfigDTO dto, ConfigState previous) {
        return new ConfigState(
                dto.getModel() != null ? normalizeModel(dto.getModel()) : previous.currentModel(),
                dto.getTemperature() != null ? clampTemperature(dto.getTemperature()) : previous.temperature(),
                dto.getMaxTokens() != null ? clampMaxTokens(dto.getMaxTokens()) : previous.maxTokens(),
                dto.getTopP() != null ? clampTopP(dto.getTopP()) : previous.topP(),
                dto.getSystemPrompt() != null ? dto.getSystemPrompt() : previous.systemPrompt());
    }

    private static String normalizeModel(String model) {
        if (model == null) {
            return null;
        }
        String normalized = model.trim();
        if (!StringUtils.hasText(normalized) || !AVAILABLE_MODEL_IDS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的AI模型: " + model);
        }
        return normalized;
    }

    private static double clampTemperature(Double value) {
        requireFinite(value, "temperature");
        return Math.max(0, Math.min(2, value));
    }

    private static int clampMaxTokens(Integer value) {
        return Math.max(1, Math.min(32000, value));
    }

    private static double clampTopP(Double value) {
        requireFinite(value, "topP");
        return Math.max(0, Math.min(1, value));
    }

    private static void requireFinite(Double value, String field) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException(field + "必须是有限数字");
        }
    }

    private static <T> T valueOr(T value, T fallback) {
        return value == null ? fallback : value;
    }

    /** Ensure direct service calls cannot accidentally run an unscoped mapper query. */
    private <T> T withTenantContext(Long tenantId, Supplier<T> operation) {
        Long previousTenant = TenantContextHolder.get();
        boolean changed = !Objects.equals(previousTenant, tenantId);
        boolean previousReadOnly = TenantContextHolder.isReadOnlySwitch();
        boolean previousAllTenants = TenantContextHolder.isAllTenantsReadOnly();
        if (changed) {
            TenantContextHolder.set(tenantId);
        }
        try {
            return operation.get();
        }
        finally {
            if (changed) {
                if (previousTenant == null) {
                    TenantContextHolder.clear();
                }
                else {
                    TenantContextHolder.set(previousTenant);
                }
                TenantContextHolder.setReadOnlySwitch(previousReadOnly);
                TenantContextHolder.setAllTenantsReadOnly(previousAllTenants);
            }
        }
    }

    private ModelConfigVO.ModelInfo createModelInfo(String id, String name, String description) {
        ModelConfigVO.ModelInfo info = new ModelConfigVO.ModelInfo();
        info.setId(id);
        info.setName(name);
        info.setDescription(description);
        return info;
    }

    private record ConfigState(String currentModel, Double temperature, Integer maxTokens,
                               Double topP, String systemPrompt) {
    }
}
