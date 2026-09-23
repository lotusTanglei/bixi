package com.lotus.bixi.workflow.event;

import com.lotus.bixi.common.mq.reliable.DurableMessage;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.workflow.api.event.WorkflowActorSnapshot;
import com.lotus.bixi.workflow.api.event.WorkflowCompleted;
import com.lotus.bixi.workflow.api.event.WorkflowEvent;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.api.event.WorkflowEventType;
import com.lotus.bixi.workflow.api.event.WorkflowOutcome;
import com.lotus.bixi.workflow.api.event.WorkflowStartRejected;
import com.lotus.bixi.workflow.api.event.WorkflowStarted;
import com.lotus.bixi.workflow.api.vo.ProcessInstanceVO;
import org.springframework.beans.factory.annotation.Qualifier;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** Builds immutable workflow lifecycle events and persists them in the workflow transaction. */
public final class WorkflowEventRecorder implements WorkflowTerminalEventSink {
    public static final String SOURCE_OWNER = "workflow";
    public static final String TARGET_OWNER = "upms";
    public static final String PROCESS_KEY = "demo_leave_approval";
    public static final String BUSINESS_TABLE = "demo_leave_request";

    private final JdbcOutboxStore outbox;
    private final WorkflowEventCodec codec;

    public WorkflowEventRecorder(@Qualifier("workflowOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        this.outbox = outbox;
        this.codec = codec;
    }

    public void recordStarted(WorkflowEvent requested, ProcessInstanceVO process) {
        WorkflowEvent event = new WorkflowEvent(
                eventId(process.getProcessInstanceId(), "started"), WorkflowEventType.WORKFLOW_STARTED, 1,
                SOURCE_OWNER, TARGET_OWNER, "default", process.getProcessInstanceId(), PROCESS_KEY,
                BUSINESS_TABLE, process.getBusinessId(), process.getBusinessKey(), requested.round(),
                requested.commandId(), 1, Instant.now(), requested.correlationId(), requested.eventId(),
                requested.actor(), new WorkflowStarted(requested.payload().requestHash()));
        enqueue(event, "process:" + process.getProcessInstanceId() + ":started");
    }

    public void recordRejected(WorkflowEvent requested, String errorCode) {
        WorkflowEvent event = new WorkflowEvent(
                eventId(requested.businessKey(), "rejected"), WorkflowEventType.WORKFLOW_START_REJECTED, 1,
                SOURCE_OWNER, TARGET_OWNER, "default", null, PROCESS_KEY, BUSINESS_TABLE,
                requested.businessId(), requested.businessKey(), requested.round(), requested.commandId(), 1,
                Instant.now(), requested.correlationId(), requested.eventId(), requested.actor(),
                new WorkflowStartRejected(requested.payload().requestHash(), errorCode));
        enqueue(event, "command:" + requested.commandId() + ":rejected");
    }

    @Override
    public void recordCompleted(String processInstanceId, long businessId, String businessKey, int round,
            String commandId, long actorId, String actorName, String requestHash, WorkflowOutcome outcome,
            Instant endedAt, String correlationId, String causationId) {
        WorkflowActorSnapshot actor = new WorkflowActorSnapshot(actorId, actorName, "default", "upms", endedAt);
        WorkflowEvent event = new WorkflowEvent(
                eventId(processInstanceId, "terminal"), WorkflowEventType.WORKFLOW_COMPLETED, 1,
                SOURCE_OWNER, TARGET_OWNER, "default", processInstanceId, PROCESS_KEY, BUSINESS_TABLE,
                businessId, businessKey, round, commandId, 2, endedAt, correlationId, causationId, actor,
                new WorkflowCompleted(requestHash, outcome, endedAt));
        enqueue(event, "process:" + processInstanceId + ":terminal");
    }

    private void enqueue(WorkflowEvent event, String dedupKey) {
        String payload = new String(codec.encode(event), StandardCharsets.UTF_8);
        DurableMessage message = DurableMessage.create(event.sourceOwner(), event.targetOwner(), event.eventId(),
                event.type().name(), event.schemaVersion(), payload);
        outbox.enqueue(message, dedupKey, event.businessKey(), event.aggregateSequence());
    }

    private static String eventId(String identity, String suffix) {
        return UUID.nameUUIDFromBytes((identity + ":" + suffix).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
