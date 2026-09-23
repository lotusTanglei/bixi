package com.lotus.bixi.common.core.sensitive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveWordEngineTest {

	@Test
	void keepsTriesSeparatedByTenant() {
		SensitiveWordEngine engine = new SensitiveWordEngine();
		engine.reload(1L, List.of("secret", "secretary"));
		engine.reload(2L, List.of("blocked"));

		assertThat(engine.containsSensitiveWord(1L, "a secretary")).isTrue();
		assertThat(engine.containsSensitiveWord(1L, "blocked")).isFalse();
		assertThat(engine.replaceSensitiveWords(1L, "secretary", "*")).isEqualTo("*");
	}

}
