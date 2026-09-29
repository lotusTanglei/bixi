package com.lotus.bixi.generator.service.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Publishes a complete generation bundle or restores the previous filesystem state. */
public final class AtomicGeneratorWriter {

	@FunctionalInterface
	interface ArtifactPublisher {
		void publish(Path staged, Path target) throws IOException;
	}

	private final GeneratorOutputPolicy outputPolicy;

	private final Path projectRoot;

	private final ArtifactPublisher publisher;

	public AtomicGeneratorWriter(GeneratorOutputPolicy outputPolicy, Path projectRoot) {
		this(outputPolicy, projectRoot, (staged, target) -> Files.move(staged, target,
			StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
	}

	AtomicGeneratorWriter(GeneratorOutputPolicy outputPolicy, Path projectRoot, ArtifactPublisher publisher) {
		this.outputPolicy = outputPolicy;
		this.publisher = publisher;
		try {
			this.projectRoot = projectRoot.toRealPath();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("项目根目录不存在", ex);
		}
	}

	public List<Path> write(List<GeneratedArtifact> artifacts, boolean overwrite) {
		if (artifacts == null || artifacts.isEmpty()) {
			throw new IllegalArgumentException("生成结果不能为空");
		}
		List<Path> targets = outputPolicy.resolveAll(artifacts.stream()
			.map(GeneratedArtifact::relativePath)
			.toList());
		preflight(targets, overwrite);

		Path workDirectory = null;
		List<PublishState> states = new ArrayList<>(artifacts.size());
		List<Path> createdDirectories = new ArrayList<>();
		try {
			workDirectory = Files.createTempDirectory(projectRoot, ".bixi-generator-");
			Path stagedDirectory = Files.createDirectories(workDirectory.resolve("staged"));
			Path backupDirectory = Files.createDirectories(workDirectory.resolve("backup"));

			for (int index = 0; index < artifacts.size(); index++) {
				Path staged = stagedDirectory.resolve(Integer.toString(index));
				Files.write(staged, artifacts.get(index).content());
			}

			for (int index = 0; index < artifacts.size(); index++) {
				Path target = targets.get(index);
				Path backup = null;
				if (Files.exists(target)) {
					backup = backupDirectory.resolve(Integer.toString(index));
					Files.move(target, backup, StandardCopyOption.ATOMIC_MOVE);
				}
				PublishState state = new PublishState(target, backup);
				states.add(state);
				createParentDirectories(target.getParent(), createdDirectories);
				publisher.publish(stagedDirectory.resolve(Integer.toString(index)), target);
				state.published = true;
			}

			deleteTree(workDirectory);
			return List.copyOf(targets);
		}
		catch (Exception ex) {
			rollback(states, createdDirectories, ex);
			if (workDirectory != null) {
				try {
					deleteTree(workDirectory);
				}
				catch (IOException cleanupError) {
					ex.addSuppressed(cleanupError);
				}
			}
			throw new IllegalStateException("生成文件发布失败，已回滚", ex);
		}
	}

	private void preflight(List<Path> targets, boolean overwrite) {
		List<Path> conflicts = targets.stream().filter(Files::exists).toList();
		if (!overwrite && !conflicts.isEmpty()) {
			throw new IllegalStateException("目标文件已存在，未经确认不能覆盖: " + conflicts);
		}
		for (Path conflict : conflicts) {
			if (!Files.isRegularFile(conflict)) {
				throw new IllegalStateException("生成目标不是普通文件，不能覆盖: " + conflict);
			}
		}
	}

	private void createParentDirectories(Path parent, List<Path> createdDirectories) throws IOException {
		List<Path> missing = new ArrayList<>();
		Path current = parent;
		while (current != null && current.startsWith(projectRoot) && !Files.exists(current)) {
			missing.add(current);
			current = current.getParent();
		}
		Files.createDirectories(parent);
		createdDirectories.addAll(missing);
	}

	private void rollback(List<PublishState> states, List<Path> createdDirectories, Exception original) {
		for (int index = states.size() - 1; index >= 0; index--) {
			PublishState state = states.get(index);
			try {
				Files.deleteIfExists(state.target);
				if (state.backup != null && Files.exists(state.backup)) {
					createParentDirectories(state.target.getParent(), new ArrayList<>());
					Files.move(state.backup, state.target, StandardCopyOption.ATOMIC_MOVE);
				}
			}
			catch (IOException rollbackError) {
				original.addSuppressed(rollbackError);
			}
		}
		createdDirectories.stream().distinct().sorted(Comparator.reverseOrder()).forEach(directory -> {
			try {
				Files.deleteIfExists(directory);
			}
			catch (IOException rollbackError) {
				original.addSuppressed(rollbackError);
			}
		});
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private static final class PublishState {

		private final Path target;

		private final Path backup;

		private boolean published;

		private PublishState(Path target, Path backup) {
			this.target = target;
			this.backup = backup;
		}
	}
}
