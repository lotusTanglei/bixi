package com.lotus.bixi.upms.demo.leave.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.upms.demo.leave.service.LeaveRequestService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/** Gateway removes external FROM headers. Single never exposes this internal HTTP endpoint. */
@RestController
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "cloud", matchIfMissing = true)
@RequestMapping("/demo/leave/internal")
public class LeaveWorkflowResultController {
    private final LeaveRequestService leaves;

    @Inner
    @PostMapping("/workflow-result")
    public R<Void> receive(@Valid @RequestBody WorkflowResultDTO result) {
        leaves.receive(result);
        return R.ok();
    }
}
