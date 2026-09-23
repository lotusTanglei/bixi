package com.lotus.bixi.workflow.api.vo;

import java.time.Instant;

/** A quarantined wire message retained for operator diagnosis and explicit replay. */
public record WorkflowQuarantineSnapshotVO(String evidenceId, String bodyJson, String reason, Instant quarantinedAt) {
}
