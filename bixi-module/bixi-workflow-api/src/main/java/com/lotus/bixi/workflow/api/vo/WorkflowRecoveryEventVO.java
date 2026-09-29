package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Redacted durable-event metadata. The wire body and payload are intentionally absent. */
public record WorkflowRecoveryEventVO(
        String direction, String owner, String eventId, String peerOwner, String type,
        int schemaVersion, String payloadHash, String status, int attempts,
        Long businessId, String businessKey, String processInstanceId, String requestId,
        String operationId, String compensationId, Instant nextAttemptAt, Instant leaseUntil,
        String lastError, Instant createdAt, Instant completedAt) {
}
