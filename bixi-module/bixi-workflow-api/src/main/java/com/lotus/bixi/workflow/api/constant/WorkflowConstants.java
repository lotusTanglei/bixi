package com.lotus.bixi.workflow.api.constant;

public interface WorkflowConstants {

    String APPROVAL_TYPE_APPROVE = "approve";

    String APPROVAL_TYPE_REJECT = "reject";

    String APPROVAL_TYPE_TRANSFER = "transfer";

    String APPROVAL_TYPE_DELEGATE = "delegate";

    String STATUS_RUNNING = "running";

    String STATUS_COMPLETED = "completed";

    String STATUS_TERMINATED = "terminated";

    Integer SUSPENSION_STATE_ACTIVE = 1;

    Integer SUSPENSION_STATE_SUSPENDED = 0;

    String WORKFLOW_SERVICE = "bixi-workflow";
}
