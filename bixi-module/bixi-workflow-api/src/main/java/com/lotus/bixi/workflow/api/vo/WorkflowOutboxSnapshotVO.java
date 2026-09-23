package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Metadata exposed to workflow operators; payload contents are intentionally omitted. */
public record WorkflowOutboxSnapshotVO(String sourceOwner, String eventId, String targetOwner, String type,
        int schemaVersion, String payloadHash, String status, int attempts, Instant nextAttemptAt,
        Instant leaseUntil, String lastError, Instant createdAt, Instant deliveredAt) {
}
