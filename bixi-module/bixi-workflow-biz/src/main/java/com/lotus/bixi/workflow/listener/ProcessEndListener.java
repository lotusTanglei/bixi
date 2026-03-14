package com.lotus.bixi.workflow.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.common.workflow.listener.BaseExecutionListener;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.service.ProcessInstanceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@AllArgsConstructor
public class ProcessEndListener extends BaseExecutionListener {

    private final ProcessInstanceService processInstanceService;

    @Override
    protected void doNotify(DelegateExecution execution) {
        String eventName = execution.getEventName();
        String processInstanceId = execution.getProcessInstanceId();

        log.info("流程执行事件 - eventName: {}, processInstanceId: {}", eventName, processInstanceId);

        if (EVENTNAME_END.equals(eventName)) {
            updateProcessInstanceStatus(processInstanceId);
        }
    }

    private void updateProcessInstanceStatus(String processInstanceId) {
        log.info("流程结束，更新业务状态 - processInstanceId: {}", processInstanceId);

        processInstanceService.update(Wrappers.<WfProcessInstance>lambdaUpdate()
                .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_COMPLETED)
                .set(WfProcessInstance::getEndTime, LocalDateTime.now())
                .eq(WfProcessInstance::getProcessInstanceId, processInstanceId));
    }
}
