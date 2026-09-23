package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.upms.api.dto.CandidateIdentity;
import com.lotus.bixi.upms.api.service.CandidateIdentityQueryService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Cloud adapter for candidate identity lookups.
 */
@FeignClient(contextId = "remoteCandidateIdentityService", value = ServiceNameConstants.UPMS_SERVICE,
        primary = false)
public interface RemoteCandidateIdentityService extends CandidateIdentityQueryService {

    @NoToken
    @Override
    @GetMapping("/internal/upms/candidate-identities/{userId}")
    CandidateIdentity findById(@PathVariable("userId") Long userId);

}
