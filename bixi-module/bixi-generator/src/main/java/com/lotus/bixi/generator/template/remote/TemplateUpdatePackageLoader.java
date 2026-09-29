package com.lotus.bixi.generator.template.remote;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public final class TemplateUpdatePackageLoader {

	private static final Pattern REVISION = Pattern.compile("[0-9a-f]{40}");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final Pattern SOURCE_PATH = Pattern.compile("[A-Za-z0-9._/-]{1,256}");
	private static final int MAX_FILES = 64;
	private static final int MAX_MANIFEST_BYTES = 64 * 1024;
	private static final int MAX_FILE_BYTES = 1024 * 1024;
	private static final int MAX_GENERATOR_PATH_LENGTH = 255;
	private static final ObjectMapper JSON = JsonMapper.builder()
			.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
			.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private final BixiGeneratorDefaultProperties properties;
	private final TemplateSourceDownloader downloader;

	public TemplateUpdatePackageLoader(BixiGeneratorDefaultProperties properties,
			TemplateSourceDownloader downloader) {
		this.properties = properties;
		this.downloader = downloader;
	}

	public TemplateUpdatePackage load() {
		return load(inspect());
	}

	public TemplateUpdatePackage load(VerifiedManifest verified) {
		if (verified == null) throw new IllegalArgumentException("在线模板manifest不能为空");
		List<TemplateUpdatePackage.TemplateFile> templates = new ArrayList<>(verified.manifest().templates().size());
		for (TemplateUpdateManifest.FileEntry entry : verified.manifest().templates()) {
			byte[] bytes = downloader.download(resolve(verified.root(), entry.sourcePath()),
					properties.getOnlineMaxFileBytes());
			if (bytes.length != entry.size()) {
				throw new IllegalArgumentException("在线模板文件大小不匹配: " + entry.sourcePath());
			}
			if (!entry.sha256().equals(sha256(bytes))) {
				throw new IllegalArgumentException("在线模板文件SHA-256不匹配: " + entry.sourcePath());
			}
			templates.add(new TemplateUpdatePackage.TemplateFile(entry.templateName(), entry.sourcePath(),
					entry.generatorPath(), entry.sha256(), utf8(bytes, entry.sourcePath())));
		}
		return new TemplateUpdatePackage(verified.manifest().revision(), verified.digest(),
				verified.manifest().groupName(), templates);
	}

	public VerifiedManifest inspect() {
		Source source = source();
		byte[] bytes = downloader.download(resolve(source.root(), properties.getOnlineManifestPath()),
				properties.getOnlineMaxManifestBytes());
		String digest = sha256(bytes);
		if (!digest.equals(properties.getOnlineManifestSha256())) {
			throw new IllegalArgumentException("在线模板manifest SHA-256不匹配");
		}
		TemplateUpdateManifest manifest;
		try {
			manifest = JSON.readValue(bytes, TemplateUpdateManifest.class);
		} catch (Exception invalid) {
			throw new IllegalArgumentException("在线模板manifest无效", invalid);
		}
		validateManifest(manifest);
		return new VerifiedManifest(source.root(), digest, manifest);
	}

	private Source source() {
		if (!REVISION.matcher(properties.getOnlineRevision()).matches()) {
			throw new IllegalArgumentException("在线模板revision必须是固定的40位小写提交哈希");
		}
		if (!SHA256.matcher(properties.getOnlineManifestSha256()).matches()) {
			throw new IllegalArgumentException("在线模板manifest SHA-256无效");
		}
		positiveLimits();
		URI base;
		try {
			base = URI.create(properties.getOnlineUrl());
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException("在线模板地址无效", invalid);
		}
		if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
				|| base.getQuery() != null || base.getFragment() != null || (base.getPort() != -1 && base.getPort() != 443)) {
			throw new IllegalArgumentException("在线模板地址必须使用HTTPS");
		}
		String host = base.getHost().toLowerCase(Locale.ROOT);
		Set<String> allowed = new HashSet<>();
		List<String> configuredHosts = properties.getOnlineAllowedHosts();
		if (configuredHosts != null) {
			for (String configured : configuredHosts) {
				if (configured != null && !configured.isBlank()) allowed.add(configured.toLowerCase(Locale.ROOT));
			}
		}
		if (!allowed.contains(host)) throw new IllegalArgumentException("在线模板主机不在白名单");
		validateSourcePath(properties.getOnlineManifestPath());
		String root = base.toString().replaceAll("/+$", "") + "/" + properties.getOnlineRevision() + "/";
		return new Source(URI.create(root));
	}

	private void positiveLimits() {
		if (properties.getOnlineMaxFiles() <= 0 || properties.getOnlineMaxFiles() > MAX_FILES
				|| properties.getOnlineMaxManifestBytes() <= 0
				|| properties.getOnlineMaxManifestBytes() > MAX_MANIFEST_BYTES
				|| properties.getOnlineMaxFileBytes() <= 0 || properties.getOnlineMaxFileBytes() > MAX_FILE_BYTES) {
			throw new IllegalArgumentException("在线模板大小或数量限制无效");
		}
	}

	private void validateManifest(TemplateUpdateManifest manifest) {
		if (manifest == null || manifest.schemaVersion() != 1) {
			throw new IllegalArgumentException("在线模板manifest版本无效");
		}
		if (!properties.getOnlineRevision().equals(manifest.revision())) {
			throw new IllegalArgumentException("在线模板manifest revision不匹配");
		}
		requiredText(manifest.groupName(), 64, "模板组名称");
		if (manifest.templates() == null || manifest.templates().isEmpty()
				|| manifest.templates().size() > properties.getOnlineMaxFiles()) {
			throw new IllegalArgumentException("在线模板数量无效");
		}
		Set<String> names = new HashSet<>();
		Set<String> sources = new HashSet<>();
		Set<String> outputs = new HashSet<>();
		for (TemplateUpdateManifest.FileEntry entry : manifest.templates()) {
			if (entry == null) throw new IllegalArgumentException("在线模板条目无效");
			requiredText(entry.templateName(), 128, "模板名称");
			validateSourcePath(entry.sourcePath());
			validateGeneratorPath(entry.generatorPath());
			if (!SHA256.matcher(entry.sha256() == null ? "" : entry.sha256()).matches()) {
				throw new IllegalArgumentException("在线模板SHA-256无效: " + entry.sourcePath());
			}
			if (entry.size() <= 0 || entry.size() > properties.getOnlineMaxFileBytes()) {
				throw new IllegalArgumentException("在线模板文件大小无效: " + entry.sourcePath());
			}
			unique(names, entry.templateName());
			unique(sources, entry.sourcePath());
			unique(outputs, entry.generatorPath());
		}
	}

	private static URI resolve(URI root, String path) {
		URI resolved = root.resolve(path);
		if (!root.getScheme().equalsIgnoreCase(resolved.getScheme()) || !root.getHost().equalsIgnoreCase(resolved.getHost())
				|| resolved.getPort() != root.getPort() || !resolved.getPath().startsWith(root.getPath())) {
			throw new IllegalArgumentException("在线模板路径越界");
		}
		return resolved;
	}

	private static void validateSourcePath(String path) {
		if (path == null || !SOURCE_PATH.matcher(path).matches() || path.startsWith("/")
				|| path.contains("..") || path.contains("//")) {
			throw new IllegalArgumentException("在线模板来源路径无效");
		}
	}

	private static void validateGeneratorPath(String path) {
		if (path == null || path.isBlank() || path.length() > MAX_GENERATOR_PATH_LENGTH || path.startsWith("/")
				|| path.startsWith("\\")
				|| path.contains("..") || path.contains("\\") || path.indexOf('\0') >= 0) {
			throw new IllegalArgumentException("在线模板生成路径无效");
		}
	}

	private static void requiredText(String value, int maximum, String field) {
		if (value == null || value.isBlank() || value.length() > maximum) {
			throw new IllegalArgumentException(field + "无效");
		}
	}

	private static void unique(Set<String> values, String value) {
		if (!values.add(value)) throw new IllegalArgumentException("在线模板manifest存在重复项: " + value);
	}

	private static String utf8(byte[] bytes, String path) {
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException invalid) {
			throw new IllegalArgumentException("在线模板不是有效UTF-8: " + path);
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256不可用", impossible);
		}
	}

	private record Source(URI root) {
	}

	public record VerifiedManifest(URI root, String digest, TemplateUpdateManifest manifest) {
	}
}
