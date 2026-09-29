package com.lotus.bixi.generator.dto;

import com.lotus.bixi.generator.entity.GenTable;

public record GenTableImportResult(boolean created, GenTable table) {
}
