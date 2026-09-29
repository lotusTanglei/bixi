package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Persisted, replayable outcome of an operator recovery request. */
public record WorkflowRecoveryResultVO(
        String requestId, String owner, String resourceType, String resourceId,
        String previousStatus, String currentStatus, boolean changed,
        String outcome, String detail, Instant completedAt) {
}
