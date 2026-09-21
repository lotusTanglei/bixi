package com.lotus.bixi.workflow.api.event;

public record WorkflowStarted(String requestHash) implements WorkflowPayload {
    public WorkflowStarted {
        WorkflowEventValidation.hash(requestHash);
    }
}
