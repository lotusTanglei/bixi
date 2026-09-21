package com.lotus.bixi.upms.demo.leave.local;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.upms.demo.leave.service.LeaveRequestService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.service.WorkflowResultReceiver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Primary
@Validated
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "single")
public class LocalWorkflowResultReceiver implements WorkflowResultReceiver {
    private final LeaveRequestService leaves;

    @Override
    public R<Void> receive(WorkflowResultDTO result) {
        leaves.receive(result);
        return R.ok();
    }
}
