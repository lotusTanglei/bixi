package com.lotus.bixi.upms.api.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysOauthClientDetailsSecretContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void acceptsClientSecretFromManagementInput() throws Exception {
		SysOauthClientDetails details = objectMapper.readValue("{\"clientSecret\":\"secret\"}",
				SysOauthClientDetails.class);

		assertThat(details.getClientSecret()).isEqualTo("secret");
	}

	@Test
	void keepsClientSecretInInternalOauthTransportButOmitsARedactedValue() throws Exception {
		SysOauthClientDetails details = new SysOauthClientDetails();
		details.setClientSecret("internal-secret");

		assertThat(objectMapper.writeValueAsString(details)).contains("\"clientSecret\":\"internal-secret\"");

		details.setClientSecret(null);

		assertThat(objectMapper.writeValueAsString(details)).doesNotContain("clientSecret");
	}

	@Test
	void marksClientSecretAsWriteOnlyAndExcludedFromExcel() throws Exception {
		var field = SysOauthClientDetails.class.getDeclaredField("clientSecret");

		assertThat(field.getAnnotation(Schema.class).accessMode()).isEqualTo(Schema.AccessMode.WRITE_ONLY);
		assertThat(field.isAnnotationPresent(ExcelIgnore.class)).isTrue();
	}

}
