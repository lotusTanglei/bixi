package com.lotus.bixi.generator.template.remote;

import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateUpdatePackageLoaderTest {

	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

	@Test
	void loadsOnlyThePinnedManifestAndVerifiedTemplateFiles() {
		byte[] entity = "public class ${className} {}".getBytes(StandardCharsets.UTF_8);
		byte[] mapper = "package ${packageName};".getBytes(StandardCharsets.UTF_8);
		String manifest = manifest("""
				{"templateName":"entity","sourcePath":"templates/entity.java.vm","generatorPath":"src/${packagePath}/${className}.java","sha256":"%s","size":%d},
				{"templateName":"mapper","sourcePath":"templates/mapper.java.vm","generatorPath":"src/${packagePath}/${className}Mapper.java","sha256":"%s","size":%d}
				""".formatted(sha256(entity), entity.length, sha256(mapper), mapper.length));
		BixiGeneratorDefaultProperties properties = properties(manifest);
		MemoryDownloader downloader = new MemoryDownloader(Map.of(
				uri("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8),
				uri("templates/entity.java.vm"), entity,
				uri("templates/mapper.java.vm"), mapper));

		TemplateUpdatePackage result = new TemplateUpdatePackageLoader(properties, downloader).load();

		assertThat(result.revision()).isEqualTo(REVISION);
		assertThat(result.manifestDigest()).isEqualTo(sha256(manifest.getBytes(StandardCharsets.UTF_8)));
		assertThat(result.groupName()).isEqualTo("bixi-default");
		assertThat(result.templates()).extracting(TemplateUpdatePackage.TemplateFile::templateName)
				.containsExactly("entity", "mapper");
		assertThat(result.templates()).extracting(TemplateUpdatePackage.TemplateFile::content)
				.containsExactly(new String(entity, StandardCharsets.UTF_8), new String(mapper, StandardCharsets.UTF_8));
		assertThat(downloader.requests).containsExactly(
				uri("manifest.json"), uri("templates/entity.java.vm"), uri("templates/mapper.java.vm"));
	}

	@Test
	void rejectsUntrustedSourcesBeforeDownloading() {
		String manifest = manifest("""
				{"templateName":"entity","sourcePath":"templates/entity.java.vm","generatorPath":"entity.java","sha256":"%s","size":1}
				""".formatted(sha256("x".getBytes(StandardCharsets.UTF_8))));
		BixiGeneratorDefaultProperties properties = properties(manifest);
		properties.setOnlineUrl("http://templates.example/bixi");
		MemoryDownloader downloader = new MemoryDownloader(Map.of());

		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties, downloader).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("HTTPS");
		assertThat(downloader.requests).isEmpty();

		properties.setOnlineUrl("https://untrusted.example/bixi");
		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties, downloader).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("白名单");
		assertThat(downloader.requests).isEmpty();
	}

	@Test
	void rejectsInvalidManifestOrTemplateBeforeReturningAPackage() {
		byte[] content = "safe".getBytes(StandardCharsets.UTF_8);
		String traversing = manifest("""
				{"templateName":"entity","sourcePath":"../secret","generatorPath":"entity.java","sha256":"%s","size":%d}
				""".formatted(sha256(content), content.length));
		MemoryDownloader traversalDownloader = downloader(traversing, Map.of());
		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties(traversing), traversalDownloader).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("路径");

		String wrongHash = manifest("""
				{"templateName":"entity","sourcePath":"templates/entity.java.vm","generatorPath":"entity.java","sha256":"%s","size":%d}
				""".formatted("0".repeat(64), content.length));
		MemoryDownloader hashDownloader = downloader(wrongHash,
				Map.of("templates/entity.java.vm", content));
		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties(wrongHash), hashDownloader).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SHA-256");

		String duplicateOutput = manifest("""
				{"templateName":"one","sourcePath":"templates/one.vm","generatorPath":"same.java","sha256":"%s","size":%d},
				{"templateName":"two","sourcePath":"templates/two.vm","generatorPath":"same.java","sha256":"%s","size":%d}
				""".formatted(sha256(content), content.length, sha256(content), content.length));
		MemoryDownloader duplicateDownloader = downloader(duplicateOutput, Map.of());
		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties(duplicateOutput), duplicateDownloader).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("重复");
	}

	@Test
	void rejectsLimitsAboveTheSupportedPackageBoundary() {
		String manifest = manifest("""
				{"templateName":"entity","sourcePath":"templates/entity.java.vm","generatorPath":"entity.java","sha256":"%s","size":1}
				""".formatted(sha256("x".getBytes(StandardCharsets.UTF_8))));
		BixiGeneratorDefaultProperties properties = properties(manifest);
		properties.setOnlineMaxFiles(65);

		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties, new MemoryDownloader(Map.of())).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("限制");

		properties.setOnlineMaxFiles(64);
		properties.setOnlineMaxFileBytes(1024 * 1024 + 1);
		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties, new MemoryDownloader(Map.of())).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("限制");
	}

	@Test
	void rejectsGeneratorPathsThatCannotFitTheDatabaseColumn() {
		byte[] content = "safe".getBytes(StandardCharsets.UTF_8);
		String manifest = manifest("""
				{"templateName":"entity","sourcePath":"templates/entity.java.vm","generatorPath":"%s","sha256":"%s","size":%d}
				""".formatted("a".repeat(256), sha256(content), content.length));

		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties(manifest),
				new MemoryDownloader(Map.of(uri("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8)))).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("生成路径");
	}

	@Test
	void treatsANullHostAllowlistAsEmpty() {
		String manifest = manifest("""
				{"templateName":"entity","sourcePath":"templates/entity.java.vm","generatorPath":"entity.java","sha256":"%s","size":1}
				""".formatted(sha256("x".getBytes(StandardCharsets.UTF_8))));
		BixiGeneratorDefaultProperties properties = properties(manifest);
		properties.setOnlineAllowedHosts(null);

		assertThatThrownBy(() -> new TemplateUpdatePackageLoader(properties, new MemoryDownloader(Map.of())).load())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("白名单");
	}

	private static BixiGeneratorDefaultProperties properties(String manifest) {
		BixiGeneratorDefaultProperties properties = new BixiGeneratorDefaultProperties();
		properties.setOnlineUrl("https://templates.example/bixi");
		properties.setOnlineAllowedHosts(List.of("templates.example"));
		properties.setOnlineRevision(REVISION);
		properties.setOnlineManifestPath("manifest.json");
		properties.setOnlineManifestSha256(sha256(manifest.getBytes(StandardCharsets.UTF_8)));
		properties.setOnlineMaxFiles(8);
		properties.setOnlineMaxManifestBytes(16 * 1024);
		properties.setOnlineMaxFileBytes(1024);
		return properties;
	}

	private static String manifest(String templates) {
		return """
				{"schemaVersion":1,"revision":"%s","groupName":"bixi-default","templates":[%s]}
				""".formatted(REVISION, templates).trim();
	}

	private static MemoryDownloader downloader(String manifest, Map<String, byte[]> templates) {
		Map<URI, byte[]> responses = new HashMap<>();
		responses.put(uri("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
		templates.forEach((path, bytes) -> responses.put(uri(path), bytes));
		return new MemoryDownloader(responses);
	}

	private static URI uri(String path) {
		return URI.create("https://templates.example/bixi/" + REVISION + "/" + path);
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	private static final class MemoryDownloader implements TemplateSourceDownloader {
		private final Map<URI, byte[]> responses;
		private final List<URI> requests = new ArrayList<>();

		private MemoryDownloader(Map<URI, byte[]> responses) {
			this.responses = responses;
		}

		@Override
		public byte[] download(URI uri, int maximumBytes) {
			requests.add(uri);
			byte[] bytes = responses.get(uri);
			if (bytes == null) throw new IllegalArgumentException("unexpected URI: " + uri);
			if (bytes.length > maximumBytes) throw new IllegalArgumentException("response too large");
			return bytes;
		}
	}
}
