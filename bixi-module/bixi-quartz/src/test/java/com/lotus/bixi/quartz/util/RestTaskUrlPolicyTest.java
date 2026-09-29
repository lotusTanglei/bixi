package com.lotus.bixi.quartz.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestTaskUrlPolicyTest {

	@Test
	void acceptsOnlyHttpAndHttps() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("");

		assertThatThrownBy(() -> policy.validate("file:///etc/passwd"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HTTP或HTTPS");
		assertThatThrownBy(() -> policy.validate("ftp://example.com/file"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HTTP或HTTPS");
	}

	@Test
	void rejectsCredentialsAndMissingHosts() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("");

		assertThatThrownBy(() -> policy.validate("https://user:secret@example.com/"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("用户凭据");
		assertThatThrownBy(() -> policy.validate("https:///missing-host"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("主机名");
	}

	@Test
	void rejectsPrivateLoopbackLinkLocalAndMetadataAddresses() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("");
		List<String> blocked = List.of(
				"127.0.0.1",
				"10.0.0.1",
				"172.16.0.1",
				"192.168.0.1",
				"169.254.169.254",
				"100.100.100.200",
				"::1",
				"::ffff:127.0.0.1",
				"fc00::1");

		for (String host : blocked) {
			assertThatThrownBy(() -> policy.validate(url(host)))
				.as("host %s", host)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("禁止");
		}
	}

	@Test
	void rejectsMetadataNamesBeforeDnsResolution() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("metadata,metadata.google.internal");

		assertThatThrownBy(() -> policy.validate("http://metadata/latest/meta-data"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("元数据");
		assertThatThrownBy(() -> policy.validate("http://metadata.google.internal/computeMetadata/v1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("元数据");
	}

	@Test
	void exactAllowlistCanPermitARestrictedHost() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("internal.example",
			host -> new InetAddress[] { InetAddress.getByName("10.0.0.2") });

		assertThatCode(() -> policy.validate("http://internal.example/health"))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> policy.validate("http://other.example/health"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("禁止");
	}

	@Test
	void rejectsAHostWhenAnyDnsResultIsRestricted() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("",
			host -> new InetAddress[] {
				InetAddress.getByName("93.184.216.34"),
				InetAddress.getByName("10.0.0.2")
			});

		assertThatThrownBy(() -> policy.validate("https://mixed.example/"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("禁止");
	}

	@Test
	void doesNotTreatAHostNameAllowlistAsAWildcard() {
		RestTaskUrlPolicy policy = new RestTaskUrlPolicy("internal.example",
			host -> new InetAddress[] { InetAddress.getByName("127.0.0.1") });

		assertThatThrownBy(() -> policy.validate("http://internal.example.evil/"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("禁止");
	}

	private static String url(String host) {
		return host.contains(":") ? "http://[" + host + "]" : "http://" + host;
	}

}
