package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** Metadata exposed to workflow operators; payload contents are intentionally omitted. */
public record WorkflowInboxSnapshotVO(String targetOwner, String eventId, String sourceOwner, String type,
        int schemaVersion, String payloadHash, String status, int attempts, Instant nextAttemptAt,
        Instant leaseUntil, String lastError, Instant receivedAt, Instant processedAt) {
}
