package com.lotus.bixi.ai.service.impl;

import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.service.ModelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private volatile String currentModel = AiConstants.DEFAULT_MODEL;
    private volatile Double temperature = 0.7;
    private volatile Integer maxTokens = 2000;
    private volatile Double topP = 0.9;
    private volatile String systemPrompt = "";

    @Override
    public ModelConfigVO getConfig() {
        ModelConfigVO vo = new ModelConfigVO();
        vo.setCurrentModel(currentModel);
        vo.setTemperature(temperature);
        vo.setMaxTokens(maxTokens);
        vo.setTopP(topP);
        vo.setSystemPrompt(systemPrompt);
        vo.setAvailableModels(listModels());
        return vo;
    }

    @Override
    public List<ModelConfigVO.ModelInfo> listModels() {
        return Arrays.asList(
                createModelInfo("qwen-turbo", "通义千问-Turbo", "快速响应，适合简单对话"),
                createModelInfo("qwen-plus", "通义千问-Plus", "平衡性能，适合日常使用"),
                createModelInfo("qwen-max", "通义千问-Max", "最强能力，适合复杂任务"),
                createModelInfo("qwen-long", "通义千问-Long", "超长上下文，适合长文档处理")
        );
    }

    @Override
    public void updateConfig(ModelConfigDTO dto) {
        if (dto.getModel() != null) {
            this.currentModel = dto.getModel();
        }
        if (dto.getTemperature() != null) {
            this.temperature = Math.max(0, Math.min(2, dto.getTemperature()));
        }
        if (dto.getMaxTokens() != null) {
            this.maxTokens = Math.max(1, Math.min(32000, dto.getMaxTokens()));
        }
        if (dto.getTopP() != null) {
            this.topP = Math.max(0, Math.min(1, dto.getTopP()));
        }
        if (dto.getSystemPrompt() != null) {
            this.systemPrompt = dto.getSystemPrompt();
        }
    }

    private ModelConfigVO.ModelInfo createModelInfo(String id, String name, String description) {
        ModelConfigVO.ModelInfo info = new ModelConfigVO.ModelInfo();
        info.setId(id);
        info.setName(name);
        info.setDescription(description);
        return info;
    }
}
