package com.lotus.bixi.common.core.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesPayloadCodecTest {

	@Test
	void roundTripUsesFrontendCompatibleCodec() {
		String encrypted = AesPayloadCodec.encrypt("{\"name\":\"bixi\"}", "test-key");
		assertThat(encrypted).isNotBlank();
		assertThat(AesPayloadCodec.decrypt(encrypted, "test-key")).isEqualTo("{\"name\":\"bixi\"}");
	}

	@Test
	void rejectsInvalidCiphertext() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> AesPayloadCodec.decrypt("bad", "test-key"));
	}

}
