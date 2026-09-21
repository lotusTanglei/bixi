package com.lotus.bixi.workflow.listener;

import com.lotus.bixi.common.workflow.listener.BaseExecutionListener;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

/** Compatibility delegate for deployed models; an end node may finish only one branch. */
@Slf4j
@Component
@ConditionalOnWorkflowEnabled
public class ProcessEndListener extends BaseExecutionListener {
    @Override
    protected void doNotify(DelegateExecution execution) {
        log.debug("流程节点执行事件 - eventName: {}, processInstanceId: {}",
                execution.getEventName(), execution.getProcessInstanceId());
    }
}
