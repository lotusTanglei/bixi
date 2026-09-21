package com.lotus.bixi.workflow.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.service.impl.WorkflowResultNotifier;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Runs in the engine transaction, once the whole process has completed. */
@Component
@ConditionalOnWorkflowEnabled
@RequiredArgsConstructor
public class WorkflowCompletionListener implements FlowableEventListener {
    private static final List<FlowableEngineEventType> COMPLETION_EVENTS = List.of(
            FlowableEngineEventType.PROCESS_COMPLETED,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_ERROR_END_EVENT,
            FlowableEngineEventType.PROCESS_COMPLETED_WITH_ESCALATION_END_EVENT);

    private final WfProcessInstanceMapper instances;
    private final WorkflowResultNotifier results;

    @Override
    public void onEvent(FlowableEvent event) {
        if (COMPLETION_EVENTS.contains(event.getType()) && event instanceof FlowableEngineEvent engineEvent) {
            // Synchronous start has not inserted the extension yet; start() records
            // its final state after the engine returns, in this same transaction.
            int updated = instances.update(null, Wrappers.<WfProcessInstance>lambdaUpdate()
                    .eq(WfProcessInstance::getProcessInstanceId, engineEvent.getProcessInstanceId())
                    .eq(WfProcessInstance::getStatus, WorkflowConstants.STATUS_RUNNING)
                    .set(WfProcessInstance::getStatus, WorkflowConstants.STATUS_COMPLETED)
                    .set(WfProcessInstance::getEndTime, LocalDateTime.now()));
            if (updated == 1) results.afterCommit(engineEvent.getProcessInstanceId());
        }
    }

    @Override public Collection<? extends FlowableEventType> getTypes() { return COMPLETION_EVENTS; }
    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }
}
