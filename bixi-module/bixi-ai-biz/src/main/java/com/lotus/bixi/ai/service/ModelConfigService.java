package com.lotus.bixi.ai.service;

import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;

import java.util.List;

public interface ModelConfigService {

    ModelConfigVO getConfig();

    List<ModelConfigVO.ModelInfo> listModels();

    void updateConfig(ModelConfigDTO dto);
}
