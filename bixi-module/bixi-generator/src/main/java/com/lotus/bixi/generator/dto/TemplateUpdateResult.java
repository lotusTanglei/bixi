package com.lotus.bixi.generator.dto;

public record TemplateUpdateResult(boolean installed, String revision, String manifestDigest,
		String groupName, int templateCount) {
}
