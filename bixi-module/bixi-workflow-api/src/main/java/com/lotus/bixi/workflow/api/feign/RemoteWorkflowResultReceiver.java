package com.lotus.bixi.workflow.api.feign;

import com.lotus.bixi.common.core.constant.ServiceNameConstants;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.feign.annotation.NoToken;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.service.WorkflowResultReceiver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "cloud", matchIfMissing = true)
@FeignClient(contextId = "remoteWorkflowResultReceiver", value = ServiceNameConstants.UPMS_SERVICE)
public interface RemoteWorkflowResultReceiver extends WorkflowResultReceiver {
    @Override
    @NoToken
    @PostMapping("/demo/leave/internal/workflow-result")
    R<Void> receive(@RequestBody WorkflowResultDTO result);
}
