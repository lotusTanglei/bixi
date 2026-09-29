package com.lotus.bixi.generator.service.output;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record GeneratedArtifact(String relativePath, byte[] content) {

	public GeneratedArtifact {
		Objects.requireNonNull(relativePath, "relativePath");
		Objects.requireNonNull(content, "content");
		content = content.clone();
	}

	@Override
	public byte[] content() {
		return content.clone();
	}

	public static GeneratedArtifact utf8(String relativePath, String content) {
		return new GeneratedArtifact(relativePath, Objects.requireNonNull(content, "content")
			.getBytes(StandardCharsets.UTF_8));
	}
}
