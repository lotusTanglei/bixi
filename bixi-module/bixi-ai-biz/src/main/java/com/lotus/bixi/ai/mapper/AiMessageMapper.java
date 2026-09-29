package com.lotus.bixi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lotus.bixi.ai.api.config.ConditionalOnAiEnabled;
import com.lotus.bixi.ai.api.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@ConditionalOnAiEnabled
public interface AiMessageMapper extends BaseMapper<AiMessage> {

}
