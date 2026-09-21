package com.lotus.bixi.workflow.api.event;

/** UPMS reserves and validates the leave before publishing this fixed start instruction. */
public record WorkflowStartRequested(String title, long approverId, String requestHash) implements WorkflowPayload {
    public WorkflowStartRequested {
        WorkflowEventValidation.text(title, "title", 255);
        if (approverId <= 0) throw new IllegalArgumentException("approverId无效");
        WorkflowEventValidation.hash(requestHash);
    }
}
