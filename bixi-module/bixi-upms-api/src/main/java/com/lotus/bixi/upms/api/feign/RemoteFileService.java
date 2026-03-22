package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author tanglei
 * @date 2025-01-01
 */
@FeignClient(contextId = "remoteFileService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteFileService {

}
