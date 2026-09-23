package com.lotus.bixi.common.core.config;

import com.lotus.bixi.common.core.sensitive.SensitiveWordEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Exposes tenant-aware sensitive-word services to deployments with narrow component scans. */
@AutoConfiguration
public class SensitiveWordAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	SensitiveWordEngine sensitiveWordEngine() {
		return new SensitiveWordEngine();
	}
}
