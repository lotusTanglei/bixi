package com.lotus.bixi.workflow.api.vo;

/** Result of an operator-triggered durable delivery recovery action. */
public record WorkflowRecoveryActionVO(String direction, String eventId, boolean changed) {
}
