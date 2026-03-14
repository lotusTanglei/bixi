package com.lotus.bixi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.ai.api.entity.AiEmbedding;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 向量嵌入 Mapper
 *
 * @author bixi
 * @date 2025-01-01
 */
@Mapper
public interface AiEmbeddingMapper extends BaseMapper<AiEmbedding> {

}
