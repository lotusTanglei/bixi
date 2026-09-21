package com.lotus.bixi.workflow.api.event;

public sealed interface WorkflowPayload
        permits WorkflowStartRequested, WorkflowStarted, WorkflowStartRejected, WorkflowCompleted {
    String requestHash();
}
