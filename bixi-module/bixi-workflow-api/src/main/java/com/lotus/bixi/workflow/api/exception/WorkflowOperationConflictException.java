package com.lotus.bixi.workflow.api.exception;

/** A competing workflow command already changed the same task or process. */
public class WorkflowOperationConflictException extends IllegalStateException {
    public static final String ERROR_CODE = "WORKFLOW_OPERATION_CONFLICT";
    private final String requestId;

    public WorkflowOperationConflictException(String requestId) {
        super("流程资源已被其他请求变更");
        this.requestId = requestId;
    }

    public String getRequestId() {
        return requestId;
    }
}
