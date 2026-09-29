package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.InboxDeliveryException;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.service.TrustedProcessStarter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/** Handles the trusted UPMS start command inside the workflow inbox transaction. */
public final class WorkflowStartRequestedHandler implements DurableMessageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowStartRequestedHandler.class);
    private final WorkflowEventCodec codec;
    private final TrustedProcessStarter processes;
    private final WorkflowEventRecorder recorder;

    public WorkflowStartRequestedHandler(@Qualifier("workflowEventCodec") WorkflowEventCodec codec,
            TrustedProcessStarter processes,
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
            TenantContextHolder.set(tenantId(event.tenantScope()));
            try {
                var result = processes.startTrusted(event);
                if (result.rejected()) {
                    recorder.recordRejected(event, result.rejectionCode());
                    return Result.PROCESSED;
                }
                var process = result.process();
                recorder.recordStarted(event, process);
                if ("completed".equals(process.getStatus())) {
                    recorder.recordCompleted(process.getProcessInstanceId(), process.getBusinessId(),
                            process.getBusinessKey(), event.round(), event.commandId(), event.actor().userId(),
                            event.actor().username(), event.tenantScope(), event.payload().requestHash(),
                            com.lotus.bixi.workflow.api.event.WorkflowOutcome.APPROVED,
                            process.getEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                            event.correlationId(), event.eventId());
                }
                return Result.PROCESSED;
            }
            catch (IllegalArgumentException invalid) {
                throw new InboxDeliveryException(InboxDeliveryException.Kind.PERMANENT,
                        "Workflow start command is invalid");
            }
            catch (RuntimeException failure) {
                LOG.error("Workflow start inbox handling failed eventId={} commandId={} tenantScope={} "
                                + "processKey={} cause={}", event.eventId(), event.commandId(), event.tenantScope(),
                        event.processKey(), failure.toString(), failure);
                throw failure;
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

    private static long tenantId(String tenantScope) {
        return "default".equals(tenantScope) ? 1L : Long.parseLong(tenantScope);
    }
}
