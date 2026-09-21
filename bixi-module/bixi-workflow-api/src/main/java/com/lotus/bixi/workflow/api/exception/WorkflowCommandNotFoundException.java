package com.lotus.bixi.workflow.api.exception;

public class WorkflowCommandNotFoundException extends IllegalArgumentException {
    public static final String ERROR_CODE = "WORKFLOW_COMMAND_NOT_FOUND";
    private final String requestId;
    public WorkflowCommandNotFoundException(String requestId) {
        super("未找到本次流程请求的已提交结果");
        this.requestId = requestId;
    }
    public String getRequestId() { return requestId; }
}
