package com.lotus.bixi.workflow.event;

import com.lotus.bixi.workflow.api.event.WorkflowOutcome;

import java.time.Instant;

/** Narrow terminal-event contract used by workflow operations and their legacy fallback. */
public interface WorkflowTerminalEventSink {
    void recordCompleted(String processInstanceId, long businessId, String businessKey, int round,
            String commandId, long actorId, String actorName, String requestHash, WorkflowOutcome outcome,
            Instant endedAt, String correlationId, String causationId);
}
