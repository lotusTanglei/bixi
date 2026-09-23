package com.lotus.bixi.common.core.annotation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveSerializerTest {

	@Test
	void masksSupportedTypes() {
		assertThat(SensitiveSerializer.mask("13800138000", SensitiveType.PHONE)).isEqualTo("138****8000");
		assertThat(SensitiveSerializer.mask("alice@example.com", SensitiveType.EMAIL)).isEqualTo("a***@example.com");
		assertThat(SensitiveSerializer.mask("Alice", SensitiveType.NAME)).isEqualTo("A*");
		assertThat(SensitiveSerializer.mask("secret", SensitiveType.PASSWORD)).isEqualTo("******");
	}

	@Test
	void handlesNullAndShortValues() {
		assertThat(SensitiveSerializer.mask(null, SensitiveType.PHONE)).isNull();
		assertThat(SensitiveSerializer.mask("12", SensitiveType.PHONE)).isEqualTo("***");
	}

}
