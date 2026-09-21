package com.lotus.bixi.workflow.service.local;

import com.lotus.bixi.workflow.api.vo.WorkflowCommandVO;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.lotus.bixi.workflow.api.vo.ApprovalRecordVO;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import com.lotus.bixi.workflow.api.vo.TaskVO;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import com.lotus.bixi.workflow.service.WfTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/** Single-mode adapter to the shared workflow business implementation. */
@Primary
@Service
@Validated
@RequiredArgsConstructor
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "single")
public class LocalWorkflowService implements WorkflowService {

    private final ProcessInstanceService processInstanceService;
    private final WfTaskService taskService;

    @Override
    @HasPermission("workflow_process_add")
    @SysLog("发起流程")
    public R<ProcessInstanceVO> startProcess(ProcessStartDTO dto) {
        return R.ok(processInstanceService.start(dto));
    }

    @Override
    @HasPermission({"workflow_process_view", "workflow_task_view"})
    public R<WorkflowCommandVO> getCommand(String requestId) {
        return R.ok(processInstanceService.getCommand(requestId));
    }

    @Override
    @HasPermission("workflow_process_view")
    public R<ProcessInstanceVO> getProcessInstance(String processInstanceId) {
        return R.ok(processInstanceService.getById(processInstanceId));
    }

    @Override
    @HasPermission("workflow_process_view")
    public R<List<ApprovalRecordVO>> getApprovalHistory(String processInstanceId) {
        return R.ok(processInstanceService.getApprovalHistory(processInstanceId));
    }

    @Override
    @HasPermission("workflow_task_edit")
    @SysLog("完成任务")
    public R<Void> completeTask(TaskCompleteDTO dto) {
        taskService.complete(dto);
        return R.ok();
    }

    @Override
    @HasPermission("workflow_task_edit")
    @SysLog("驳回任务")
    public R<Void> rejectTask(TaskRejectDTO dto) {
        taskService.reject(dto);
        return R.ok();
    }

    @Override
    @HasPermission("workflow_task_edit")
    @SysLog("转办任务")
    public R<Void> transferTask(TaskTransferDTO dto) {
        taskService.transfer(dto);
        return R.ok();
    }

    @Override
    @HasPermission("workflow_task_view")
    public R<Page<TaskVO>> getTodoTasks(long current, long size) {
        return R.ok(toPage(taskService.todoPage(new Page<TaskVO>(current, size), SecurityUtils.getUser().getId())));
    }

    @Override
    @HasPermission("workflow_task_view")
    public R<Page<TaskVO>> getDoneTasks(long current, long size) {
        return R.ok(toPage(taskService.donePage(new Page<TaskVO>(current, size), SecurityUtils.getUser().getId())));
    }

    private static Page<TaskVO> toPage(IPage<TaskVO> source) {
        return new Page<TaskVO>(source.getCurrent(), source.getSize(), source.getTotal())
                .setRecords(source.getRecords());
    }
}
