package com.lotus.bixi.upms.api.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

class TenantStatusSerializationTest {

	@Test
	void tenantStatusCanBeStoredByJdkRedisSerializer() {
		var status = new TenantStatusService.TenantStatus(1L, "0", LocalDateTime.now());

		assertThatCode(() -> {
			try (var bytes = new ByteArrayOutputStream(); var output = new ObjectOutputStream(bytes)) {
				output.writeObject(status);
			}
		}).doesNotThrowAnyException();
	}

}
