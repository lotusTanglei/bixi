package com.lotus.bixi.generator.template;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInTemplateResourceContractTest {

	private static final String CATALOG = "generator/default-v1/catalog.json";

	@Test
	void fixedCatalogContainsTheCompleteSingleTableContract() throws Exception {
		ClassPathResource catalogResource = new ClassPathResource(CATALOG);
		assertThat(catalogResource.exists()).isTrue();

		JSONObject catalog = JSONUtil.parseObj(catalogResource.getContentAsString(StandardCharsets.UTF_8));
		assertThat(catalog.getStr("version")).isEqualTo("bixi-default-v1");
		assertThat(catalog.getStr("source")).isEqualTo("classpath:generator/default-v1");
		assertThat(catalog.getJSONArray("templates")).hasSizeGreaterThanOrEqualTo(11);

		Set<String> kinds = new HashSet<>();
		Set<String> paths = new HashSet<>();
		for (Object value : catalog.getJSONArray("templates")) {
			JSONObject entry = (JSONObject) value;
			String kind = entry.getStr("kind");
			String outputPath = entry.getStr("outputPath");
			String resource = entry.getStr("resource");
			assertThat(kinds.add(kind)).as("duplicate kind %s", kind).isTrue();
			assertThat(paths.add(outputPath)).as("duplicate path %s", outputPath).isTrue();
			assertThat(outputPath).doesNotStartWith("/").doesNotContain("..");
			ClassPathResource template = new ClassPathResource("generator/default-v1/" + resource);
			assertThat(template.exists()).as("template resource %s", resource).isTrue();
			assertThat(template.contentLength()).isGreaterThan(0);
		}

		assertThat(kinds).contains(
			"entity", "create-dto", "update-dto", "query-dto", "vo", "mapper", "service",
			"service-impl", "controller", "menu-sql", "frontend-api", "frontend-list", "frontend-form");
	}
}
