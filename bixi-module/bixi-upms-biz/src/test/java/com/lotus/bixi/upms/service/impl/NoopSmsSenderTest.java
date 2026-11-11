package com.lotus.bixi.upms.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class NoopSmsSenderTest {

	@Test
	void missingProviderDoesNotLogMobileOrCode(CapturedOutput output) {
		assertThat(new NoopSmsSender().send("13800138000", "12345678")).isFalse();
		assertThat(output.getAll()).doesNotContain("13800138000", "12345678");
	}
}
