package com.lotus.bixi.generator.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BixiGeneratorDefaultPropertiesTest {

	@Test
	void remoteTemplateChecksAreDisabledByDefault() {
		assertThat(new BixiGeneratorDefaultProperties().isAutoCheckVersion()).isFalse();
	}

	@Test
	void generatedJavaDefaultsToTheCloudUpmsScanRoot() {
		assertThat(new BixiGeneratorDefaultProperties().getPackageName())
			.isEqualTo("com.lotus.bixi.upms");
	}
}
