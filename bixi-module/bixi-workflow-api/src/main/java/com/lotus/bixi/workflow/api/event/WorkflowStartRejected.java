package com.lotus.bixi.workflow.api.event;

import java.util.Set;

public record WorkflowStartRejected(String requestHash, String errorCode) implements WorkflowPayload {
    private static final Set<String> ERROR_CODES = Set.of("INVALID_START", "DEFINITION_UNAVAILABLE",
            "APPROVER_UNAVAILABLE", "START_FAILED");

    public WorkflowStartRejected {
        WorkflowEventValidation.hash(requestHash);
        if (errorCode == null || errorCode.length() > 64 || !ERROR_CODES.contains(errorCode)) {
            throw new IllegalArgumentException("errorCode无效");
        }
    }
}
