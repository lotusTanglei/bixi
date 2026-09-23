package com.lotus.bixi.workflow.api.event;

import java.time.Instant;

public record WorkflowBusinessTaskResult(String requestHash, String operationId, boolean success,
                                         String bookingReference, String errorCode, Instant completedAt)
        implements WorkflowPayload {
    public WorkflowBusinessTaskResult {
        WorkflowEventValidation.hash(requestHash);
        WorkflowEventValidation.uuid(operationId, "operationId");
        if (success) {
            WorkflowEventValidation.text(bookingReference, "bookingReference", 128);
            if (errorCode != null) throw new IllegalArgumentException("成功结果不能包含errorCode");
        } else {
            WorkflowEventValidation.text(errorCode, "errorCode", 64);
            if (bookingReference != null) throw new IllegalArgumentException("失败结果不能包含bookingReference");
        }
        WorkflowEventValidation.instant(completedAt, "completedAt");
    }
}
