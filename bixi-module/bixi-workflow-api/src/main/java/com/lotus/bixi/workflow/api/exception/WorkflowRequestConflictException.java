package com.lotus.bixi.workflow.api.exception;

/** The same actor's request ID was already committed with different content. */
public class WorkflowRequestConflictException extends IllegalArgumentException {
    public static final String ERROR_CODE = "WORKFLOW_REQUEST_CONFLICT";
    private final String requestId;
    public WorkflowRequestConflictException(String requestId) {
        super("同一requestId已用于不同的流程请求");
        this.requestId = requestId;
    }
    public String getRequestId() { return requestId; }
}
