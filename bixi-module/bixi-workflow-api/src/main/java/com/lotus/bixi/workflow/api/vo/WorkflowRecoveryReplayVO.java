package com.lotus.bixi.workflow.api.vo;

/** Result of an explicit operator-triggered quarantine replay. */
public record WorkflowRecoveryReplayVO(String evidenceId, String eventId, String result, boolean accepted) {
}
