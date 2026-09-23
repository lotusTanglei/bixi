package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.upms.api.service.TenantStatusService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "remoteTenantStatusService", value = ServiceNameConstants.UPMS_SERVICE, primary = false)
public interface RemoteTenantStatusService extends TenantStatusService {

	@Override
	@NoToken
	@GetMapping("/tenant/status/{tenantId}")
	TenantStatus getStatus(@PathVariable("tenantId") Long tenantId);

}
