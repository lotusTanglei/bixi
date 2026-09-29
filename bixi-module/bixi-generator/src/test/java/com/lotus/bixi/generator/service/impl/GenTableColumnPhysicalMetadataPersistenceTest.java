package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.mapper.GenFieldTypeMapper;
import com.lotus.bixi.generator.mapper.GenTableColumnMapper;
import com.lotus.bixi.generator.service.GenTableColumnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(GenTableColumnPhysicalMetadataPersistenceTest.Config.class)
class GenTableColumnPhysicalMetadataPersistenceTest {

	@Autowired GenTableColumnService columnService;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void createSchema() {
		jdbc.execute("DROP TABLE IF EXISTS gen_table_column");
		jdbc.execute("""
				CREATE TABLE gen_table_column (
				  id BIGINT PRIMARY KEY,
				  field_name VARCHAR(200), field_type VARCHAR(200), field_comment VARCHAR(200),
				  primary_pk CHAR(1), attr_name VARCHAR(200), attr_type VARCHAR(200), package_name VARCHAR(500),
				  del_flag CHAR(1) DEFAULT '0'
				)
				""");
		jdbc.update("""
				INSERT INTO gen_table_column
				(id, field_name, field_type, field_comment, primary_pk, attr_name, attr_type, package_name, del_flag)
				VALUES (11, 'old_name', 'VARCHAR', 'old comment', '0', 'oldName', 'String', 'java.lang.String', '0')
				""");
	}

	@Test
	void explicitPhysicalMetadataUpdateClearsNullableValuesAndReloadsThemAsNull() {
		GenTableColumn physical = new GenTableColumn();
		physical.setId(11L);
		physical.setFieldName("current_name");
		physical.setFieldType("JSON");
		physical.setFieldComment(null);
		physical.setPrimaryPk("1");
		physical.setAttrName("currentName");
		physical.setAttrType("Object");
		physical.setPackageName(null);

		assertThat(columnService.updatePhysicalMetadataById(physical)).isTrue();

		Map<String, Object> reloaded = jdbc.queryForMap("""
				SELECT field_name, field_type, field_comment, primary_pk, attr_name, attr_type, package_name
				FROM gen_table_column WHERE id = 11
				""");
		assertThat(reloaded)
			.containsEntry("FIELD_NAME", "current_name")
			.containsEntry("FIELD_TYPE", "JSON")
			.containsEntry("PRIMARY_PK", "1")
			.containsEntry("ATTR_NAME", "currentName")
			.containsEntry("ATTR_TYPE", "Object")
			.containsEntry("FIELD_COMMENT", null)
			.containsEntry("PACKAGE_NAME", null);
	}

	@Configuration(proxyBeanMethods = false)
	@MapperScan(basePackageClasses = GenTableColumnMapper.class)
	@ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
	static class Config {

		@Bean DataSource dataSource() {
			return new DriverManagerDataSource("jdbc:h2:mem:generator-column-physical-" + UUID.randomUUID()
				+ ";MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", "");
		}

		@Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean GenTableColumnService columnService(GenTableColumnMapper mapper) {
			GenTableColumnServiceImpl service = new GenTableColumnServiceImpl(mock(GenFieldTypeMapper.class));
			ReflectionTestUtils.setField(service, "baseMapper", mapper);
			return service;
		}

	}

}
