package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Redacted owner-side automatic business-task state. */
public record WorkflowRecoveryBusinessTaskVO(
        String owner, String operationId, String processInstanceId, String businessTable,
        Long businessId, Integer round, String status, String resultEventId,
        String compensationId, Instant deadline, String lastError, Instant createdAt,
        Instant updatedAt) {
}
