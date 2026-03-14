package com.lotus.bixi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.ai.api.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 对话记录 Mapper
 *
 * @author bixi
 * @date 2025-01-01
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

}
