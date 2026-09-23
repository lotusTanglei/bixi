package com.lotus.bixi.workflow.api.event;

import java.time.Instant;

public record WorkflowBusinessTaskRequested(String requestHash, String operationId, String executionId,
                                            String activityId, int activityOccurrence, Instant deadline)
        implements WorkflowPayload {
    public WorkflowBusinessTaskRequested {
        WorkflowEventValidation.hash(requestHash);
        WorkflowEventValidation.uuid(operationId, "operationId");
        WorkflowEventValidation.text(executionId, "executionId", 64);
        WorkflowEventValidation.text(activityId, "activityId", 128);
        if (activityOccurrence <= 0) throw new IllegalArgumentException("activityOccurrence无效");
        WorkflowEventValidation.instant(deadline, "deadline");
    }
}
