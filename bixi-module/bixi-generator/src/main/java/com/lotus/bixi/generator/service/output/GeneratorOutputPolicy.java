package com.lotus.bixi.generator.service.output;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Resolves generated files inside explicit repository directories. */
public final class GeneratorOutputPolicy {

	private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");

	private final Path projectRoot;

	private final List<Path> allowedRoots;

	public GeneratorOutputPolicy(Path projectRoot, List<Path> allowedRoots) {
		try {
			this.projectRoot = projectRoot.toRealPath();
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("项目根目录不存在", ex);
		}
		if (allowedRoots == null || allowedRoots.isEmpty()) {
			throw new IllegalArgumentException("生成目录白名单不能为空");
		}
		this.allowedRoots = allowedRoots.stream().map(this::resolveAllowedRoot).toList();
	}

	public Path resolve(String renderedPath) {
		if (!StringUtils.hasText(renderedPath)) {
			throw new IllegalArgumentException("生成路径不能为空");
		}
		if (renderedPath.indexOf('\\') >= 0 || WINDOWS_ABSOLUTE_PATH.matcher(renderedPath).matches()) {
			throw new IllegalArgumentException("生成路径必须使用项目内相对路径");
		}

		final Path relativePath;
		try {
			relativePath = Path.of(renderedPath);
		}
		catch (InvalidPathException ex) {
			throw new IllegalArgumentException("生成路径无效", ex);
		}
		if (relativePath.isAbsolute()) {
			throw new IllegalArgumentException("生成路径必须使用项目内相对路径");
		}
		for (Path segment : relativePath) {
			if ("..".equals(segment.toString())) {
				throw new IllegalArgumentException("生成路径不能包含目录穿越");
			}
		}

		Path target = projectRoot.resolve(relativePath).normalize();
		if (allowedRoots.stream().noneMatch(target::startsWith)) {
			throw new IllegalArgumentException("生成路径不在允许目录内");
		}
		ensureNoSymbolicLink(target);
		return target;
	}

	public List<Path> resolveAll(List<String> renderedPaths) {
		if (renderedPaths == null) {
			throw new IllegalArgumentException("生成路径不能为空");
		}
		List<Path> targets = new ArrayList<>(renderedPaths.size());
		Set<Path> uniqueTargets = new HashSet<>();
		for (String renderedPath : renderedPaths) {
			Path target = resolve(renderedPath);
			if (!uniqueTargets.add(target)) {
				throw new IllegalArgumentException("生成结果包含重复目标路径: " + renderedPath);
			}
			targets.add(target);
		}
		return List.copyOf(targets);
	}

	private Path resolveAllowedRoot(Path allowedRoot) {
		if (allowedRoot == null || allowedRoot.isAbsolute()) {
			throw new IllegalArgumentException("允许目录必须是项目内相对路径");
		}
		Path resolved = projectRoot.resolve(allowedRoot).normalize();
		if (resolved.equals(projectRoot) || !resolved.startsWith(projectRoot)) {
			throw new IllegalArgumentException("允许目录必须位于项目根目录内");
		}
		return resolved;
	}

	private void ensureNoSymbolicLink(Path target) {
		Path relative = projectRoot.relativize(target);
		Path current = projectRoot;
		for (Path segment : relative) {
			current = current.resolve(segment);
			if (Files.isSymbolicLink(current)) {
				throw new IllegalArgumentException("生成路径不能经过符号链接");
			}
			if (!Files.exists(current)) {
				return;
			}
		}
	}
}
