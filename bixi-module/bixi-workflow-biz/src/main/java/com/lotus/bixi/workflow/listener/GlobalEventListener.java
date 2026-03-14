package com.lotus.bixi.workflow.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.engine.impl.persistence.entity.ExecutionEntityImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GlobalEventListener implements FlowableEventListener {

    @Override
    public void onEvent(FlowableEvent event) {
        FlowableEventType eventType = event.getType();
        
        log.info("全局事件监听 - eventType: {}", eventType);

        switch (eventType.name()) {
            case "TASK_CREATED":
                handleTaskCreated(event);
                break;
            case "TASK_COMPLETED":
                handleTaskCompleted(event);
                break;
            case "PROCESS_STARTED":
                handleProcessStarted(event);
                break;
            case "PROCESS_COMPLETED":
                handleProcessCompleted(event);
                break;
            default:
                log.debug("未处理的事件类型: {}", eventType);
        }
    }

    private void handleTaskCreated(FlowableEvent event) {
        if (event instanceof FlowableEntityEvent) {
            FlowableEntityEvent entityEvent = (FlowableEntityEvent) event;
            Object entity = entityEvent.getEntity();
            if (entity instanceof TaskEntityImpl) {
                TaskEntityImpl task = (TaskEntityImpl) entity;
                log.info("处理任务创建事件 - processInstanceId: {}", task.getProcessInstanceId());
            }
        }
    }

    private void handleTaskCompleted(FlowableEvent event) {
        if (event instanceof FlowableEntityEvent) {
            FlowableEntityEvent entityEvent = (FlowableEntityEvent) event;
            Object entity = entityEvent.getEntity();
            if (entity instanceof TaskEntityImpl) {
                TaskEntityImpl task = (TaskEntityImpl) entity;
                log.info("处理任务完成事件 - processInstanceId: {}", task.getProcessInstanceId());
            }
        }
    }

    private void handleProcessStarted(FlowableEvent event) {
        if (event instanceof FlowableEntityEvent) {
            FlowableEntityEvent entityEvent = (FlowableEntityEvent) event;
            Object entity = entityEvent.getEntity();
            if (entity instanceof ExecutionEntityImpl) {
                ExecutionEntityImpl execution = (ExecutionEntityImpl) entity;
                log.info("处理流程启动事件 - processInstanceId: {}", execution.getProcessInstanceId());
            }
        }
    }

    private void handleProcessCompleted(FlowableEvent event) {
        if (event instanceof FlowableEntityEvent) {
            FlowableEntityEvent entityEvent = (FlowableEntityEvent) event;
            Object entity = entityEvent.getEntity();
            if (entity instanceof ExecutionEntityImpl) {
                ExecutionEntityImpl execution = (ExecutionEntityImpl) entity;
                log.info("处理流程完成事件 - processInstanceId: {}", execution.getProcessInstanceId());
            }
        }
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
