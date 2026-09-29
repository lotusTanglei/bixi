package com.lotus.bixi.generator.template.remote;

import java.util.List;

public record TemplateUpdatePackage(String revision, String manifestDigest, String groupName,
		List<TemplateFile> templates) {

	public TemplateUpdatePackage {
		templates = List.copyOf(templates);
	}

	public record TemplateFile(String templateName, String sourcePath, String generatorPath,
			String sha256, String content) {
	}
}
