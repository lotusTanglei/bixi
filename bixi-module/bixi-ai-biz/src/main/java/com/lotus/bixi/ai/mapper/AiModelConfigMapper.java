package com.lotus.bixi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Persistence contract for the single active model configuration per tenant. */
@Mapper
@ConditionalOnAiEnabled
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    /**
     * Update only fields supplied by the caller. The statement is atomic, so
     * two cloud instances changing different fields do not overwrite each
     * other's values after reading a stale snapshot.
     */
    @Update("""
            UPDATE ai_model_config
               SET current_model = COALESCE(#{model}, current_model),
                   temperature = COALESCE(#{temperature}, temperature),
                   max_tokens = COALESCE(#{maxTokens}, max_tokens),
                   top_p = COALESCE(#{topP}, top_p),
                   system_prompt = COALESCE(#{systemPrompt}, system_prompt),
                   update_by = #{updateBy},
                   update_time = CURRENT_TIMESTAMP
             WHERE tenant_id = #{tenantId}
               AND del_flag = '0'
            """)
    int updateValues(@Param("tenantId") Long tenantId,
                     @Param("model") String model,
                     @Param("temperature") Double temperature,
                     @Param("maxTokens") Integer maxTokens,
                     @Param("topP") Double topP,
                     @Param("systemPrompt") String systemPrompt,
                     @Param("updateBy") Long updateBy);

    default AiModelConfig selectByTenantId(Long tenantId) {
        return selectOne(Wrappers.<AiModelConfig>lambdaQuery()
                .eq(AiModelConfig::getTenantId, tenantId)
                .eq(AiModelConfig::getDelFlag, "0")
                .last("LIMIT 1"));
    }
}
