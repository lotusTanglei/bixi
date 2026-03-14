package com.lotus.bixi.ai.api.feign;

import com.lotus.bixi.ai.api.constant.AiConstants;
import com.lotus.bixi.ai.api.dto.ChatDTO;
import com.lotus.bixi.ai.api.vo.ChatVO;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * AI 远程服务接口
 *
 * @author bixi
 * @date 2025-01-01
 */
@FeignClient(contextId = "remoteAiService", value = AiConstants.AI_SERVICE)
public interface RemoteAiService {

    @NoToken
    @GetMapping("/ai/chat")
    R<ChatVO> chat(@SpringQueryMap ChatDTO request);

}
