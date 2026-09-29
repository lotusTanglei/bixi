package com.lotus.bixi.generator.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.util.R;
import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GenDatasourceConfigSecretContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void acceptsPasswordFromCreateAndUpdateInput() throws Exception {
		GenDatasourceConfig config = objectMapper.readValue("{\"password\":\"database-secret\"}",
				GenDatasourceConfig.class);

		assertThat(config.getPassword()).isEqualTo("database-secret");
	}

	@Test
	void managementDetailListAndPageOmitPassword() throws Exception {
		GenDatasourceConfig config = new GenDatasourceConfig();
		config.setPassword("database-secret");
		Page<GenDatasourceConfig> page = new Page<>();
		page.setRecords(List.of(config));

		for (Object response : List.of(R.ok(config), R.ok(List.of(config)), R.ok(page))) {
			String json = objectMapper.writeValueAsString(response);
			assertThat(json).doesNotContain("password", "database-secret");
		}
	}

	@Test
	void marksPasswordAsWriteOnlyAndExcludedFromExcel() throws Exception {
		var field = GenDatasourceConfig.class.getDeclaredField("password");

		assertThat(field.getAnnotation(JsonProperty.class).access()).isEqualTo(JsonProperty.Access.WRITE_ONLY);
		assertThat(field.getAnnotation(Schema.class).accessMode()).isEqualTo(Schema.AccessMode.WRITE_ONLY);
		assertThat(field.isAnnotationPresent(ExcelIgnore.class)).isTrue();
	}

}
