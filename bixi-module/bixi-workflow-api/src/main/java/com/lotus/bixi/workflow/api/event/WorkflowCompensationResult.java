package com.lotus.bixi.workflow.api.event;

import java.time.Instant;

public record WorkflowCompensationResult(String requestHash, String operationId, String compensationId,
                                          boolean success, String errorCode, Instant completedAt)
        implements WorkflowPayload {
    public WorkflowCompensationResult {
        WorkflowEventValidation.hash(requestHash);
        WorkflowEventValidation.uuid(operationId, "operationId");
        WorkflowEventValidation.uuid(compensationId, "compensationId");
        if (!success) WorkflowEventValidation.text(errorCode, "errorCode", 64);
        else if (errorCode != null) throw new IllegalArgumentException("成功补偿不能包含errorCode");
        WorkflowEventValidation.instant(completedAt, "completedAt");
    }
}
