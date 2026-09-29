package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Redacted command inventory row; request and response payloads are never returned. */
public record WorkflowRecoveryCommandVO(
        String owner, String commandId, String requestId, String operation, String resourceId,
        String status, String processInstanceId, Long businessId, String businessKey,
        Long actorId, String error, Instant createdAt, Instant completedAt) {
}
