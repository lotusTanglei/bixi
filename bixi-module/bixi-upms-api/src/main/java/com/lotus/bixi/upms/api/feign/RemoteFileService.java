package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.upms.api.entity.SysDictItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * @author tanglei
 * @description TODO
 * @date 2024/12/9 22:46
 */
@FeignClient(contextId = "remoteFileService", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteFileService {

}
