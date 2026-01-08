package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.feign.annotation.NoToken;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * @author 唐磊
 * @date 2025-01-01
 * <p>
 * 查询参数相关
 */
@FeignClient(contextId = "remoteParamService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteParamService {

    /**
     * 通过key 查询参数配置
     *
     * @param key key
     * @NoToken 声明成内部调用，避免MQ 等无法调用
     */
    @NoToken
    @GetMapping("/param/publicValue/{key}")
    R<String> getByKey(@PathVariable("key") String key);

}
