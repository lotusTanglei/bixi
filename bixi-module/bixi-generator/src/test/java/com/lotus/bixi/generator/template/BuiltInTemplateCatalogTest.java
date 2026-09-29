package com.lotus.bixi.generator.template;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuiltInTemplateCatalogTest {

	@Test
	void loadsOneImmutableVersionedSnapshotFromClasspath() {
		BuiltInTemplateCatalog.Snapshot snapshot = new BuiltInTemplateCatalog().snapshot();

		assertThat(snapshot.catalogVersion()).isEqualTo("bixi-default-v1");
		assertThat(snapshot.source()).isEqualTo("classpath:generator/default-v1");
		assertThat(snapshot.templates()).hasSize(17);
		assertThat(snapshot.templates())
			.allSatisfy(template -> {
				assertThat(template.getId()).isPositive();
				assertThat(template.getTemplateName()).contains("bixi-default-v1");
				assertThat(template.getTemplateDesc()).isEqualTo(snapshot.source());
				assertThat(template.getGeneratorPath()).isNotBlank();
				assertThat(template.getTemplateCode()).isNotBlank();
			});
		assertThat(snapshot.templates()).extracting(template -> template.getGeneratorPath())
			.doesNotHaveDuplicates();
		assertThat(snapshot.templates()).extracting(template -> template.getTemplateName())
			.contains("import-dto@bixi-default-v1", "import-result@bixi-default-v1",
				"import-row-error@bixi-default-v1", "export-vo@bixi-default-v1");
		assertThat(snapshot.templates()).extracting(template -> template.getGeneratorPath())
			.anyMatch(path -> path.endsWith("${ClassName}ImportDTO.java"))
			.anyMatch(path -> path.endsWith("${ClassName}ImportResult.java"))
			.anyMatch(path -> path.endsWith("${ClassName}ImportRowError.java"))
			.anyMatch(path -> path.endsWith("${ClassName}ExportVO.java"));
		assertThat(snapshot.templates()).isUnmodifiable();
	}

	@Test
	void rejectsMissingDuplicateAbsoluteAndTraversingCatalogEntries() {
		assertThatThrownBy(() -> catalog(singleEntry("entity", "entity.java", "missing.vm"), Map.of()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("资源");

		String duplicateOutput = """
				{"kind":"entity","outputPath":"same.java","resource":"entity.vm"},
				{"kind":"mapper","outputPath":"same.java","resource":"mapper.vm"}
				""";
		assertThatThrownBy(() -> catalog(duplicateOutput,
				Map.of("entity.vm", "entity", "mapper.vm", "mapper")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("生成路径重复");

		assertThatThrownBy(() -> catalog(singleEntry("entity", "/absolute.java", "entity.vm"),
				Map.of("entity.vm", "entity")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("生成路径无效");

		assertThatThrownBy(() -> catalog(singleEntry("entity", "entity.java", "../entity.vm"),
				Map.of("../entity.vm", "entity")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("资源路径无效");
	}

	private static BuiltInTemplateCatalog catalog(String templates, Map<String, String> resources) {
		Map<String, String> source = new HashMap<>(resources);
		source.put("catalog.json", """
				{"version":"bixi-default-v1","source":"classpath:generator/default-v1","templates":[%s],
				 "parentChildTemplates":[{"kind":"child","outputPath":"child.java","resource":"child.vm"}]}
				""".formatted(templates));
		source.put("child.vm", "child");
		return new BuiltInTemplateCatalog(source::get);
	}

	private static String singleEntry(String kind, String outputPath, String resource) {
		return """
				{"kind":"%s","outputPath":"%s","resource":"%s"}
				""".formatted(kind, outputPath, resource).trim();
	}
}
