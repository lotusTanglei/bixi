package com.lotus.bixi.generator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GenTableImportRequest(
		@NotBlank
		@Size(max = 64)
		@Pattern(regexp = "[A-Za-z0-9._:-]+", message = "归属标记格式无效")
		String author) {
}
