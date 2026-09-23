package com.lotus.bixi.workflow.api.vo;

/** Redacted correlation metadata used to reconcile durable delivery with business state. */
public record WorkflowRecoveryReconciliationVO(String owner, String eventId, String eventType,
        String businessTable, Long businessId, String businessKey, String processInstanceId,
        Integer round, String requestId, String commandStatus, String operationId,
        String businessTaskStatus, String compensationId, String quarantineEvidenceId,
        String durableStatus, String businessStatus, String classification, String detail) {
}
