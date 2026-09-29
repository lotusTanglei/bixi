package com.lotus.bixi.generator.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorOutputPolicyTest {

	@TempDir
	Path projectRoot;

	@Test
	void resolvesAFileInsideAnAllowedProjectDirectory() throws IOException {
		Files.createDirectories(projectRoot.resolve("bixi-ui/src/api"));
		GeneratorOutputPolicy policy = policy();

		Path target = policy.resolve("bixi-ui/src/api/demo.ts");

		assertThat(target).isEqualTo(projectRoot.toRealPath().resolve("bixi-ui/src/api/demo.ts"));
	}

	@Test
	void rejectsAbsoluteAndTraversalPaths() throws IOException {
		Files.createDirectories(projectRoot.resolve("bixi-ui"));
		GeneratorOutputPolicy policy = policy();

		assertThatThrownBy(() -> policy.resolve(projectRoot.resolve("bixi-ui/demo.ts").toString()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("相对路径");
		assertThatThrownBy(() -> policy.resolve("C:\\temp\\demo.ts"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("相对路径");
		assertThatThrownBy(() -> policy.resolve("../outside.txt"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("目录穿越");
		assertThatThrownBy(() -> policy.resolve("bixi-ui/../../outside.txt"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("目录穿越");
		assertThatThrownBy(() -> policy.resolve("bixi-ui/../bixi-module/inside.txt"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("目录穿越");
	}

	@Test
	void rejectsAllowedRootPrefixCollisions() throws IOException {
		Files.createDirectories(projectRoot.resolve("bixi-ui-evil"));

		assertThatThrownBy(() -> policy().resolve("bixi-ui-evil/payload.txt"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("允许目录");
	}

	@Test
	void rejectsExistingSymlinkThatEscapesProjectRoot() throws IOException {
		Path allowed = Files.createDirectories(projectRoot.resolve("bixi-ui/src"));
		Path outside = Files.createTempDirectory(projectRoot.getParent(), "generator-outside-");
		Files.createSymbolicLink(allowed.resolve("linked"), outside);

		assertThatThrownBy(() -> policy().resolve("bixi-ui/src/linked/payload.txt"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("符号链接");
	}

	@Test
	void rejectsTraversalBeforeDuplicateNormalization() throws IOException {
		Files.createDirectories(projectRoot.resolve("bixi-ui/src/api"));

		assertThatThrownBy(() -> policy().resolveAll(List.of(
			"bixi-ui/src/api/demo.ts",
			"bixi-ui/src/views/../api/demo.ts"
		)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("目录穿越");
	}

	private GeneratorOutputPolicy policy() {
		return new GeneratorOutputPolicy(projectRoot, List.of(
			Path.of("bixi-module"),
			Path.of("bixi-ui"),
			Path.of("bixi-project-documents/sql")
		));
	}
}
