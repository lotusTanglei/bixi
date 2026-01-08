

package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.upms.api.entity.SysLog;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@FeignClient(contextId = "remoteLogService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteLogService {

    /**
     * 保存日志 (异步多线程调用，无token)
     *
     * @param sysLog 日志实体
     * @return succes、false
     */
    @NoToken
    @PostMapping("/log/save")
    R<Boolean> saveLog(@RequestBody SysLog sysLog);

}
