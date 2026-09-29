package com.lotus.bixi.generator.service.impl;

import com.lotus.bixi.generator.entity.GenTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorTemplateSnapshotTest {

	@Test
	void versionIsStableWhenTheMapperReturnsTheSameTemplatesInAnotherOrder() {
		GenTemplate first = template(11L, "first", "a/b", "content-a");
		GenTemplate second = template(12L, "second", "c/d", "content-b");

		assertThat(GeneratorTemplateSnapshot.version(7L, List.of(first, second)))
			.isEqualTo(GeneratorTemplateSnapshot.version(7L, List.of(second, first)));
	}

	@Test
	void versionUsesUnambiguousFieldBoundaries() {
		GenTemplate left = template(1L, "left", "23", "4");
		GenTemplate right = template(12L, "right", "3", "4");

		assertThat(GeneratorTemplateSnapshot.version(7L, List.of(left)))
			.isNotEqualTo(GeneratorTemplateSnapshot.version(7L, List.of(right)));
	}

	@Test
	void orderedReturnsTheDeterministicPublicationOrder() {
		GenTemplate first = template(11L, "first", "a/b", "content-a");
		GenTemplate second = template(12L, "second", "c/d", "content-b");

		assertThat(GeneratorTemplateSnapshot.ordered(List.of(second, first))).containsExactly(first, second);
	}

	private GenTemplate template(Long id, String name, String path, String code) {
		GenTemplate template = new GenTemplate();
		template.setId(id);
		template.setTemplateName(name);
		template.setGeneratorPath(path);
		template.setTemplateCode(code);
		return template;
	}
}
