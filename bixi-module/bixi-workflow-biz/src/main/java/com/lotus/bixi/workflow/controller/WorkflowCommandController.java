package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;
import com.lotus.bixi.workflow.command.WorkflowCommandExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnWorkflowEnabled
@RequiredArgsConstructor
@RequestMapping("/workflow/command")
public class WorkflowCommandController {
    private final WorkflowCommandExecutor commands;
    @GetMapping("/{requestId}")
    @HasPermission({"workflow_process_view", "workflow_task_view"})
    public R<WorkflowCommandVO> getCommand(@PathVariable String requestId) {
        return R.ok(commands.getCommand(requestId));
    }
}
