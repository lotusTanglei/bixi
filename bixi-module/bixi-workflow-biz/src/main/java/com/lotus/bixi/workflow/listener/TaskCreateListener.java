package com.lotus.bixi.workflow.listener;

import com.lotus.bixi.common.workflow.listener.BaseTaskListener;
import lombok.extern.slf4j.Slf4j;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TaskCreateListener extends BaseTaskListener {

    @Override
    protected void doNotify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String assignee = delegateTask.getAssignee();
        String processInstanceId = delegateTask.getProcessInstanceId();

        log.info("任务创建 - taskId: {}, taskName: {}, assignee: {}, processInstanceId: {}",
                taskId, taskName, assignee, processInstanceId);

        if (assignee != null) {
            sendNotification(assignee, taskName, processInstanceId);
        }
    }

    private void sendNotification(String assignee, String taskName, String processInstanceId) {
        log.info("发送通知给审批人 - assignee: {}, taskName: {}, processInstanceId: {}",
                assignee, taskName, processInstanceId);
    }
}
