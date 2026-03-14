package com.lotus.bixi.workflow.listener;

import com.lotus.bixi.common.workflow.listener.BaseTaskListener;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.entity.WfApprovalRecord;
import com.lotus.bixi.workflow.service.ApprovalRecordService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class TaskCompleteListener extends BaseTaskListener {

    private final ApprovalRecordService approvalRecordService;

    @Override
    protected void doNotify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String processInstanceId = delegateTask.getProcessInstanceId();
        String assignee = delegateTask.getAssignee();

        log.info("任务完成 - taskId: {}, taskName: {}, assignee: {}, processInstanceId: {}",
                taskId, taskName, assignee, processInstanceId);

        WfApprovalRecord record = new WfApprovalRecord();
        record.setProcessInstanceId(processInstanceId);
        record.setTaskId(taskId);
        record.setTaskName(taskName);
        record.setTaskKey(delegateTask.getTaskDefinitionKey());
        record.setApprovalType(WorkflowConstants.APPROVAL_TYPE_APPROVE);
        record.setApprovalTime(LocalDateTime.now());

        if (assignee != null) {
            try {
                record.setApprovalUserId(Long.parseLong(assignee));
            } catch (NumberFormatException e) {
                log.warn("无法解析审批人ID: {}", assignee);
            }
        }

        approvalRecordService.saveRecord(record);
    }
}
