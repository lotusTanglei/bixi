package com.lotus.bixi.workflow.api.feign;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(contextId = "remoteWorkflowService", value = WorkflowConstants.WORKFLOW_SERVICE)
public interface RemoteWorkflowService {

    @PostMapping("/workflow/process/start")
    R<ProcessInstanceVO> startProcess(@RequestBody ProcessStartDTO dto);

    @PostMapping("/workflow/task/complete")
    R<Boolean> completeTask(@RequestBody TaskCompleteDTO dto);

    @PostMapping("/workflow/task/reject")
    R<Boolean> rejectTask(@RequestBody TaskRejectDTO dto);

    @PostMapping("/workflow/task/transfer")
    R<Boolean> transferTask(@RequestBody TaskTransferDTO dto);

    @GetMapping("/workflow/task/todo/{userId}")
    R<List<TaskVO>> getTodoTasks(@PathVariable("userId") Long userId);

    @GetMapping("/workflow/task/done/{userId}")
    R<List<TaskVO>> getDoneTasks(@PathVariable("userId") Long userId);

    @GetMapping("/workflow/process/instance/{processInstanceId}")
    R<ProcessInstanceVO> getProcessInstance(@PathVariable("processInstanceId") String processInstanceId);
}
