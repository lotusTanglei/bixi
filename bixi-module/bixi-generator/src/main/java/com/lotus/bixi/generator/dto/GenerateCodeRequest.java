package com.lotus.bixi.generator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Explicit mutation contract for publishing a versioned generation bundle. */
public record GenerateCodeRequest(
		@NotEmpty List<@NotNull Long> tableIds,
		@NotBlank String templateVersion,
		boolean overwrite) {
}
