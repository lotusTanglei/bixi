package com.lotus.bixi.workflow.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.constant.WorkflowConstants;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.mapper.WfProcessInstanceMapper;
import com.lotus.bixi.workflow.event.WorkflowEventRecorder;
import com.lotus.bixi.workflow.api.event.WorkflowOutcome;
import com.lotus.bixi.workflow.service.impl.WorkflowResultNotifier;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

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
    private final ObjectProvider<WorkflowEventRecorder> events;
    private final WorkflowResultNotifier compatibilityResults;

    @Override
    public void onEvent(FlowableEvent event) {
        if (COMPLETION_EVENTS.contains(event.getType()) && event instanceof FlowableEngineEvent engineEvent) {
            // Synchronous start has not inserted the extension yet; start() records
            // its final state after the engine returns, in this same transaction.
            String status = engineEvent.getType() == FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT
                    ? WorkflowConstants.STATUS_TERMINATED : WorkflowConstants.STATUS_COMPLETED;
            int updated = instances.update(null, Wrappers.<WfProcessInstance>lambdaUpdate()
                    .eq(WfProcessInstance::getProcessInstanceId, engineEvent.getProcessInstanceId())
                    .eq(WfProcessInstance::getStatus, WorkflowConstants.STATUS_RUNNING)
                    .set(WfProcessInstance::getStatus, status)
                    .set(WfProcessInstance::getEndTime, LocalDateTime.now()));
            if (updated == 1) {
                // The status update already acquired the process row lock. Read the updated
                // extension without taking a second lock, so completion callbacks do not
                // contend with the command-level process lock.
                WfProcessInstance instance = instances.selectByProcessInstanceId(engineEvent.getProcessInstanceId());
                if (instance != null && instance.getBusinessId() != null && instance.getBusinessRound() != null
                        && instance.getStartUserId() != null && instance.getStartRequestId() != null) {
                    WorkflowEventRecorder recorder = events.getIfAvailable();
                    if (recorder != null) {
                        recorder.recordCompleted(instance.getProcessInstanceId(), instance.getBusinessId(),
                                instance.getBusinessKey(), instance.getBusinessRound(), instance.getStartRequestId(),
                                instance.getStartUserId(), instance.getStartUserName(), requestHash(instance),
                                outcome(engineEvent.getType()),
                                instance.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                                instance.getStartRequestId(), engineEvent.getProcessInstanceId());
                    } else {
                        compatibilityResults.afterCommit(engineEvent.getProcessInstanceId());
                    }
                }
            }
        }
    }

    @Override public Collection<? extends FlowableEventType> getTypes() { return COMPLETION_EVENTS; }
    @Override public boolean isFailOnException() { return true; }
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    @Override public String getOnTransaction() { return null; }

    private static WorkflowOutcome outcome(FlowableEventType type) {
        return type == FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT
                ? WorkflowOutcome.CANCELED : WorkflowOutcome.APPROVED;
    }

    private static String requestHash(WfProcessInstance instance) {
        if (instance.getStartRequestHash() != null) return instance.getStartRequestHash();
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(instance.getStartRequestId()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
