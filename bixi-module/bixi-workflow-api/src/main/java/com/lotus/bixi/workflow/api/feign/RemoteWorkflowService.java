package com.lotus.bixi.workflow.api.feign;

import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "cloud", matchIfMissing = true)
@FeignClient(contextId = "remoteWorkflowService", value = WorkflowConstants.WORKFLOW_SERVICE,
        configuration = WorkflowFeignConfiguration.class)
public interface RemoteWorkflowService extends WorkflowService {

    @Override
    @PostMapping("/workflow/process/start")
    R<ProcessInstanceVO> startProcess(@RequestBody ProcessStartDTO dto);

    @Override
    @GetMapping("/workflow/command/{requestId}")
    R<WorkflowCommandVO> getCommand(@PathVariable("requestId") String requestId);

    @Override
    @PostMapping("/workflow/task/complete")
    R<Void> completeTask(@RequestBody TaskCompleteDTO dto);

    @Override
    @PostMapping("/workflow/task/reject")
    R<Void> rejectTask(@RequestBody TaskRejectDTO dto);

    @Override
    @PostMapping("/workflow/task/transfer")
    R<Void> transferTask(@RequestBody TaskTransferDTO dto);

    @Override
    @GetMapping("/workflow/task/todo/page")
    R<Page<TaskVO>> getTodoTasks(@RequestParam("current") long current, @RequestParam("size") long size);

    @Override
    @GetMapping("/workflow/task/done/page")
    R<Page<TaskVO>> getDoneTasks(@RequestParam("current") long current, @RequestParam("size") long size);

    @Override
    @GetMapping("/workflow/process/details/{processInstanceId}")
    R<ProcessInstanceVO> getProcessInstance(@PathVariable("processInstanceId") String processInstanceId);

    @Override
    @GetMapping("/workflow/process/history/{processInstanceId}")
    R<List<ApprovalRecordVO>> getApprovalHistory(@PathVariable("processInstanceId") String processInstanceId);
}
