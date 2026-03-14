package com.lotus.bixi.common.workflow.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;

/**
 * 执行监听器基类
 *
 * @author bixi
 * @date 2025-01-01
 */
@Slf4j
public abstract class BaseExecutionListener implements ExecutionListener {

    @Override
    public void notify(DelegateExecution execution) {
        String executionId = execution.getId();
        String processInstanceId = execution.getProcessInstanceId();
        String processDefinitionId = execution.getProcessDefinitionId();
        String eventName = execution.getEventName();

        log.debug("ExecutionListener triggered - executionId: {}, processInstanceId: {}, eventName: {}",
                executionId, processInstanceId, eventName);

        try {
            doNotify(execution);
        } catch (Exception e) {
            log.error("ExecutionListener execution failed - executionId: {}, processInstanceId: {}",
                    executionId, processInstanceId, e);
            throw e;
        }
    }

    /**
     * 子类实现具体的监听逻辑
     *
     * @param execution 执行对象
     */
    protected abstract void doNotify(DelegateExecution execution);

}
