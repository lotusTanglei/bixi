package com.lotus.bixi.common.feign.core;

import feign.RequestInterceptor;
import org.springframework.http.HttpHeaders;

/**
 * @author 唐磊
 * @date 2024/3/15
 * <p>
 * http connection close
 */
public class BixiFeignRequestCloseInterceptor implements RequestInterceptor {

    /**
     * set connection close
     *
     * @param template
     */
    @Override
    public void apply(feign.RequestTemplate template) {
        template.header(HttpHeaders.CONNECTION, "close");
    }

}
