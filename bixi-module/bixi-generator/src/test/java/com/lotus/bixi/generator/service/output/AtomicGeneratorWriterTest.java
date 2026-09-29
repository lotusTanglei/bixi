package com.lotus.bixi.generator.service.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicGeneratorWriterTest {

	@TempDir
	Path projectRoot;

	@Test
	void refusesExistingFilesBeforeChangingAnything() throws IOException {
		Path existing = createFile("bixi-ui/src/api/demo.ts", "old");
		AtomicGeneratorWriter writer = new AtomicGeneratorWriter(policy(), projectRoot);

		assertThatThrownBy(() -> writer.write(List.of(
			GeneratedArtifact.utf8("bixi-ui/src/api/demo.ts", "new"),
			GeneratedArtifact.utf8("bixi-ui/src/api/other.ts", "other")
		), false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("覆盖");

		assertThat(Files.readString(existing)).isEqualTo("old");
		assertThat(projectRoot.resolve("bixi-ui/src/api/other.ts")).doesNotExist();
		assertNoTemporaryDirectories();
	}

	@Test
	void publishesEveryArtifactAfterPreflight() throws IOException {
		AtomicGeneratorWriter writer = new AtomicGeneratorWriter(policy(), projectRoot);

		List<Path> written = writer.write(List.of(
			GeneratedArtifact.utf8("bixi-ui/src/api/demo.ts", "api"),
			GeneratedArtifact.utf8("bixi-module/demo/Demo.java", "class Demo {}")
		), false);

		assertThat(written).containsExactly(
			projectRoot.toRealPath().resolve("bixi-ui/src/api/demo.ts"),
			projectRoot.toRealPath().resolve("bixi-module/demo/Demo.java")
		);
		assertThat(Files.readString(written.get(0))).isEqualTo("api");
		assertThat(Files.readString(written.get(1))).isEqualTo("class Demo {}");
		assertNoTemporaryDirectories();
	}

	@Test
	void restoresPreviousFilesAndRemovesNewFilesWhenPublishingFails() throws IOException {
		Path existing = createFile("bixi-ui/src/api/demo.ts", "old");
		Files.createDirectories(projectRoot.resolve("bixi-module"));
		AtomicInteger publishes = new AtomicInteger();
		AtomicGeneratorWriter writer = new AtomicGeneratorWriter(policy(), projectRoot, (staged, target) -> {
			if (publishes.getAndIncrement() == 1) {
				throw new IOException("injected publish failure");
			}
			Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		});

		assertThatThrownBy(() -> writer.write(List.of(
			GeneratedArtifact.utf8("bixi-ui/src/api/demo.ts", "new"),
			GeneratedArtifact.utf8("bixi-module/demo/Demo.java", "class Demo {}")
		), true))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("回滚");

		assertThat(Files.readString(existing)).isEqualTo("old");
		assertThat(projectRoot.resolve("bixi-module/demo/Demo.java")).doesNotExist();
		assertNoTemporaryDirectories();
	}

	private Path createFile(String relativePath, String content) throws IOException {
		Path path = projectRoot.resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}

	private GeneratorOutputPolicy policy() throws IOException {
		Files.createDirectories(projectRoot.resolve("bixi-ui"));
		Files.createDirectories(projectRoot.resolve("bixi-module"));
		return new GeneratorOutputPolicy(projectRoot, List.of(Path.of("bixi-ui"), Path.of("bixi-module")));
	}

	private void assertNoTemporaryDirectories() throws IOException {
		try (var entries = Files.list(projectRoot)) {
			assertThat(entries.map(path -> path.getFileName().toString()))
				.noneMatch(name -> name.startsWith(".bixi-generator-"));
		}
	}
}
