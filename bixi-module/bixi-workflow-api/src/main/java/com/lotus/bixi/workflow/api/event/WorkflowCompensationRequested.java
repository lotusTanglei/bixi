package com.lotus.bixi.workflow.api.event;

public record WorkflowCompensationRequested(String requestHash, String operationId, String compensationId)
        implements WorkflowPayload {
    public WorkflowCompensationRequested {
        WorkflowEventValidation.hash(requestHash);
        WorkflowEventValidation.uuid(operationId, "operationId");
        WorkflowEventValidation.uuid(compensationId, "compensationId");
    }
}
