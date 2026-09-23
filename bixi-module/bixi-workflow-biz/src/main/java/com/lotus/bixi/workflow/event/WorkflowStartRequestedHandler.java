package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.service.impl.ProcessInstanceServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;

/** Handles the trusted UPMS start command inside the workflow inbox transaction. */
public final class WorkflowStartRequestedHandler implements DurableMessageHandler {
    private final WorkflowEventCodec codec;
    private final ProcessInstanceServiceImpl processes;
    private final WorkflowEventRecorder recorder;

    public WorkflowStartRequestedHandler(@Qualifier("workflowEventCodec") WorkflowEventCodec codec,
            ProcessInstanceServiceImpl processes,
            WorkflowEventRecorder recorder) {
        this.codec = codec;
        this.processes = processes;
        this.recorder = recorder;
    }

    @Override
    public Result handle(DurableMessage message) {
        WorkflowEvent event = decode(message);
        if (!message.eventId().equals(event.eventId()) || !message.type().equals(event.type().name())
                || message.schemaVersion() != event.schemaVersion()
                || !message.sourceOwner().equals(event.sourceOwner())
                || !message.targetOwner().equals(event.targetOwner())) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Workflow event envelope does not match its payload");
        }
        if (event.type() != WorkflowEventType.WORKFLOW_START_REQUESTED) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Workflow handler received an unexpected event type");
        }
        Long previousTenant = TenantContextHolder.get();
        try {
            TenantContextHolder.set(SecurityConstants.DEFAULT_TENANT_ID);
            try {
                var process = processes.startTrusted(event);
                recorder.recordStarted(event, process);
                if ("completed".equals(process.getStatus())) {
                    recorder.recordCompleted(process.getProcessInstanceId(), process.getBusinessId(),
                            process.getBusinessKey(), event.round(), event.commandId(), event.actor().userId(),
                            event.actor().username(), event.payload().requestHash(),
                            com.lotus.bixi.workflow.api.event.WorkflowOutcome.APPROVED,
                            process.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                            event.correlationId(), event.eventId());
                }
                return Result.PROCESSED;
            }
            catch (IllegalArgumentException rejected) {
                recorder.recordRejected(event, "INVALID_START");
                return Result.PROCESSED;
            }
        }
        finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else TenantContextHolder.set(previousTenant);
        }
    }

    private WorkflowEvent decode(DurableMessage message) {
        try {
            return codec.decode(message.payloadJson().getBytes(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException invalid) {
            throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                    "Workflow event payload is invalid");
        }
    }
}
