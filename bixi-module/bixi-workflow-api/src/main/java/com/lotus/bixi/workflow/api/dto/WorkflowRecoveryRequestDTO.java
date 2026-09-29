package com.lotus.bixi.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Stable identity and compare-and-set precondition for an operator recovery action. */
public record WorkflowRecoveryRequestDTO(
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        String requestId,
        @NotBlank @Size(max = 256) String reason,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String expectedStatus) {
}
