package com.lotus.bixi.acceptance.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
@Schema(description = "字典表导入结果")
public final class SysDictImportResult {
	public static final int MAX_ERRORS = 100;

	private final boolean success;
	private final String code;
	private final int totalRows;
	private final int importedRows;
	private final List<SysDictImportRowError> errors;

	private SysDictImportResult(boolean success, String code, int totalRows, int importedRows,
			List<SysDictImportRowError> errors) {
		this.success = success;
		this.code = Objects.requireNonNull(code, "code");
		this.totalRows = Math.max(0, totalRows);
		this.importedRows = Math.max(0, importedRows);
		this.errors = List.copyOf(Objects.requireNonNull(errors, "errors").stream()
			.limit(MAX_ERRORS).toList());
	}

	public static SysDictImportResult success(int totalRows) {
		return new SysDictImportResult(true, "SUCCESS", totalRows, totalRows, List.of());
	}

	public static SysDictImportResult failure(String code, int totalRows,
			List<SysDictImportRowError> errors) {
		return new SysDictImportResult(false, code, totalRows, 0, errors);
	}
}
