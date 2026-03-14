package com.lotus.bixi.ai.service;

import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;

public interface ModelConfigService {

    ModelConfigVO getConfig();

    void updateConfig(ModelConfigDTO dto);
}
