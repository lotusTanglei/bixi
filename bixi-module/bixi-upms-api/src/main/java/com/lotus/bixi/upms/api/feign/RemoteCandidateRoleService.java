package com.lotus.bixi.upms.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.upms.api.dto.CandidateRole;
import com.lotus.bixi.upms.api.service.CandidateRoleQueryService;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(contextId = "remoteCandidateRoleService", value = ServiceNameConstants.UPMS_SERVICE, primary = false)
public interface RemoteCandidateRoleService extends CandidateRoleQueryService {
    @NoToken
    @Override
    @GetMapping("/internal/upms/candidate-roles/{roleId}")
    CandidateRole findById(@PathVariable("roleId") Long roleId);
}
