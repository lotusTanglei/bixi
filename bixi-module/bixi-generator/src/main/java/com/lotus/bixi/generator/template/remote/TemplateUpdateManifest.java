package com.lotus.bixi.generator.template.remote;

import java.util.List;

public record TemplateUpdateManifest(int schemaVersion, String revision, String groupName, List<FileEntry> templates) {

	public TemplateUpdateManifest {
		templates = templates == null ? null : List.copyOf(templates);
	}

	public record FileEntry(String templateName, String sourcePath, String generatorPath, String sha256, long size) {
	}
}
