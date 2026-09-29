package com.lotus.bixi.generator.service.impl;

import com.lotus.bixi.generator.entity.GenTemplate;
import com.lotus.bixi.generator.service.output.GeneratedArtifact;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

final class GeneratorTemplateSnapshot {

	private GeneratorTemplateSnapshot() {
	}

	static List<GenTemplate> ordered(List<GenTemplate> templates) {
		Objects.requireNonNull(templates, "templates");
		return templates.stream()
			.peek(template -> Objects.requireNonNull(template, "template"))
			.sorted(Comparator.comparing(template -> Objects.requireNonNull(template.getId(), "template id")))
			.toList();
	}

	static String version(Long style, List<GenTemplate> templates) {
		Objects.requireNonNull(style, "style");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, Long.toString(style));
			List<GenTemplate> ordered = ordered(templates);
			update(digest, Integer.toString(ordered.size()));
			for (GenTemplate template : ordered) {
				update(digest, Long.toString(template.getId()));
				update(digest, Objects.requireNonNull(template.getGeneratorPath(), "template path"));
				update(digest, Objects.requireNonNull(template.getTemplateCode(), "template code"));
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}

	static String version(Long tableId, Long style, List<GenTemplate> templates,
			List<GeneratedArtifact> artifacts) {
		Objects.requireNonNull(tableId, "tableId");
		Objects.requireNonNull(artifacts, "artifacts");
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, Long.toString(tableId));
			update(digest, version(style, templates));
			update(digest, Integer.toString(artifacts.size()));
			for (GeneratedArtifact artifact : artifacts) {
				Objects.requireNonNull(artifact, "artifact");
				update(digest, artifact.relativePath());
				update(digest, artifact.content());
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}

	private static void update(MessageDigest digest, String value) {
		update(digest, value.getBytes(StandardCharsets.UTF_8));
	}

	private static void update(MessageDigest digest, byte[] bytes) {
		digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
		digest.update(bytes);
	}
}
