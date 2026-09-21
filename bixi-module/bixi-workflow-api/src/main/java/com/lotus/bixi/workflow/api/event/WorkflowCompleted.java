package com.lotus.bixi.workflow.api.event;

import java.time.Instant;

public record WorkflowCompleted(String requestHash, WorkflowOutcome outcome, Instant endedAt)
        implements WorkflowPayload {
    public WorkflowCompleted {
        WorkflowEventValidation.hash(requestHash);
        if (outcome == null) throw new IllegalArgumentException("outcome不能为空");
        WorkflowEventValidation.instant(endedAt, "endedAt");
    }
}
