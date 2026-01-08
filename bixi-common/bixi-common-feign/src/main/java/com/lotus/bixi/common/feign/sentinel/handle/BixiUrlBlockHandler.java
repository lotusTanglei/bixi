

package com.lotus.bixi.common.feign.sentinel.handle;

import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.util.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * sentinel统一降级限流策略
 * <p>
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@RequiredArgsConstructor
public class BixiUrlBlockHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, String resourceName, BlockException e)
            throws Exception {
        log.error("sentinel 降级 资源名称{}", resourceName, e);

        response.setContentType(MediaType.APPLICATION_JSON.getType());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.getWriter().print(objectMapper.writeValueAsString(R.failed(e.getMessage())));
    }

}
