package com.lotus.bixi.common.workflow.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;

/**
 * 任务监听器基类
 *
 * @author bixi
 * @date 2025-01-01
 */
@Slf4j
public abstract class BaseTaskListener implements TaskListener {

    @Override
    public void notify(DelegateTask delegateTask) {
        String taskId = delegateTask.getId();
        String taskName = delegateTask.getName();
        String processInstanceId = delegateTask.getProcessInstanceId();
        String eventName = delegateTask.getEventName();

        log.debug("TaskListener triggered - taskId: {}, taskName: {}, processInstanceId: {}, eventName: {}",
                taskId, taskName, processInstanceId, eventName);

        try {
            doNotify(delegateTask);
        } catch (Exception e) {
            log.error("TaskListener execution failed - taskId: {}, taskName: {}", taskId, taskName, e);
            throw e;
        }
    }

    /**
     * 子类实现具体的监听逻辑
     *
     * @param delegateTask 任务对象
     */
    protected abstract void doNotify(DelegateTask delegateTask);

}
