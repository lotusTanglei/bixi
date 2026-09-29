package com.lotus.bixi.generator.template;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lotus.bixi.generator.entity.GenTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public final class BuiltInTemplateCatalog {

	public static final long DEFAULT_GROUP_ID = 1_872_559_165_395_542_017L;
	public static final String CATALOG_VERSION = "bixi-default-v1";
	public static final String SOURCE = "classpath:generator/default-v1";
	private static final String ROOT = "generator/default-v1/";
	private static final int MAX_TEMPLATES = 64;
	private static final long MAX_TEMPLATE_BYTES = 1024 * 1024;
	private static final long TEMPLATE_ID_BASE = 9_000_100_000_000_000_000L;

	private final Snapshot snapshot;

	public BuiltInTemplateCatalog() {
		this(BuiltInTemplateCatalog::readClasspath);
	}

	BuiltInTemplateCatalog(Function<String, String> reader) {
		this.snapshot = load(reader);
	}

	public Snapshot snapshot() {
		return snapshot;
	}

	private static Snapshot load(Function<String, String> reader) {
		JSONObject catalog = JSONUtil.parseObj(read(reader, "catalog.json"));
		String version = required(catalog, "version");
		String source = required(catalog, "source");
		if (!CATALOG_VERSION.equals(version) || !SOURCE.equals(source)) {
			throw new IllegalStateException("内置模板来源或版本无效");
		}
		JSONArray entries = catalog.getJSONArray("templates");
		if (entries == null || entries.isEmpty() || entries.size() > MAX_TEMPLATES) {
			throw new IllegalStateException("内置模板数量无效");
		}
		JSONArray parentChildEntries = catalog.getJSONArray("parentChildTemplates");
		if (parentChildEntries == null || parentChildEntries.isEmpty()
				|| entries.size() + parentChildEntries.size() > MAX_TEMPLATES) {
			throw new IllegalStateException("内置父子表模板数量无效");
		}

		Set<String> resources = new HashSet<>();
		List<CatalogEntry> singleEntries = loadEntries(entries, version, source, resources, 0, reader);
		Map<String, CatalogEntry> parentChildByKind = new LinkedHashMap<>();
		for (CatalogEntry entry : singleEntries) {
			parentChildByKind.put(entry.kind(), entry);
		}
		for (CatalogEntry entry : loadEntries(parentChildEntries, version, source, resources, entries.size(), reader)) {
			parentChildByKind.put(entry.kind(), entry);
		}

		List<GenTemplate> templates = validateAndExtract(singleEntries, "单表");
		List<GenTemplate> parentChildTemplates = validateAndExtract(
			new ArrayList<>(parentChildByKind.values()), "父子表");
		return new Snapshot(version, source, templates, parentChildTemplates);
	}

	private static List<CatalogEntry> loadEntries(JSONArray entries, String version, String source,
			Set<String> resources, int idOffset, Function<String, String> reader) {
		List<CatalogEntry> loaded = new ArrayList<>(entries.size());
		Set<String> kinds = new HashSet<>();
		for (int index = 0; index < entries.size(); index++) {
			JSONObject entry = entries.getJSONObject(index);
			String kind = required(entry, "kind");
			String outputPath = required(entry, "outputPath");
			String resource = required(entry, "resource");
			requireUnique(kinds, kind, "模板类型");
			requireUnique(resources, resource, "模板资源");
			validateOutputPath(outputPath);
			validateResource(resource);

			GenTemplate template = new GenTemplate();
			template.setId(TEMPLATE_ID_BASE + idOffset + index);
			template.setTemplateName(kind + "@" + version);
			template.setTemplateDesc(source);
			template.setGeneratorPath(outputPath);
			template.setTemplateCode(read(reader, resource));
			loaded.add(new CatalogEntry(kind, template));
		}
		return loaded;
	}

	private static List<GenTemplate> validateAndExtract(List<CatalogEntry> entries, String variant) {
		Set<String> outputPaths = new HashSet<>();
		for (CatalogEntry entry : entries) {
			requireUnique(outputPaths, entry.template().getGeneratorPath(), variant + "生成路径");
		}
		return entries.stream().map(CatalogEntry::template).toList();
	}

	private static String read(Function<String, String> reader, String resource) {
		try {
			String content = reader.apply(resource);
			if (content == null || content.isEmpty()
					|| content.getBytes(StandardCharsets.UTF_8).length > MAX_TEMPLATE_BYTES) {
				throw new IllegalStateException("内置模板资源无效: " + resource);
			}
			return content;
		}
		catch (IllegalStateException failure) {
			throw failure;
		}
		catch (RuntimeException failure) {
			throw new IllegalStateException("读取内置模板失败: " + resource, failure);
		}
	}

	private static String readClasspath(String resource) {
		ClassPathResource classpath = new ClassPathResource(ROOT + resource);
		try {
			if (!classpath.exists() || classpath.contentLength() <= 0
					|| classpath.contentLength() > MAX_TEMPLATE_BYTES) {
				throw new IllegalStateException("内置模板资源无效: " + resource);
			}
			return classpath.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException failure) {
			throw new IllegalStateException("读取内置模板失败: " + resource, failure);
		}
	}

	private static String required(JSONObject object, String key) {
		String value = object == null ? null : object.getStr(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("内置模板字段不能为空: " + key);
		}
		return value;
	}

	private static void requireUnique(Set<String> values, String value, String label) {
		if (!values.add(value)) throw new IllegalStateException(label + "重复: " + value);
	}

	private static void validateOutputPath(String path) {
		if (path.length() > 512 || path.startsWith("/") || path.startsWith("\\")
				|| path.contains("..") || path.contains("\\") || path.indexOf('\0') >= 0) {
			throw new IllegalStateException("内置模板生成路径无效: " + path);
		}
	}

	private static void validateResource(String resource) {
		if (resource.length() > 128 || resource.startsWith("/") || resource.contains("..")
				|| resource.contains("/") || resource.contains("\\") || resource.indexOf('\0') >= 0) {
			throw new IllegalStateException("内置模板资源路径无效: " + resource);
		}
	}

	private record CatalogEntry(String kind, GenTemplate template) {
	}

	public record Snapshot(String catalogVersion, String source, List<GenTemplate> templates,
			List<GenTemplate> parentChildTemplates) {
	}
}
