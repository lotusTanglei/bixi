package com.lotus.bixi.workflow.api.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** An immutable command result, distinct from the process's current details. */
public record WorkflowCommandVO(String requestId, String operation, String resourceId,
        String processInstanceId, String resultCode, Instant completedAt, JsonNode response) { }
