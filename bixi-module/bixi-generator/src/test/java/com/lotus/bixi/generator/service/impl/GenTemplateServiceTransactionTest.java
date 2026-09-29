package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.mapper.GenGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateMapper;
import com.lotus.bixi.generator.service.GenTemplateService;
import com.lotus.bixi.generator.template.remote.TemplateUpdateManifest;
import com.lotus.bixi.generator.template.remote.TemplateUpdatePackage;
import com.lotus.bixi.generator.template.remote.TemplateUpdatePackageLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(GenTemplateServiceTransactionTest.Config.class)
class GenTemplateServiceTransactionTest {

	private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";
	private static final String DIGEST = "a".repeat(64);

	@Autowired GenTemplateService service;
	@Autowired TemplateUpdatePackageLoader loader;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void createSchema() {
		jdbc.execute("DROP TABLE IF EXISTS gen_template_group");
		jdbc.execute("DROP TABLE IF EXISTS gen_template");
		jdbc.execute("DROP TABLE IF EXISTS gen_group");
		jdbc.execute("""
				CREATE TABLE gen_group (
				  id BIGINT PRIMARY KEY,
				  group_name VARCHAR(255),
				  group_desc VARCHAR(255),
				  del_flag CHAR(1) DEFAULT '0'
				)
				""");
		jdbc.execute("""
				CREATE TABLE gen_template (
				  id BIGINT PRIMARY KEY,
				  template_name VARCHAR(255) NOT NULL,
				  generator_path VARCHAR(255) NOT NULL,
				  template_desc VARCHAR(255) NOT NULL,
				  template_code CLOB NOT NULL,
				  del_flag CHAR(1) DEFAULT '0'
				)
				""");
		jdbc.execute("""
				CREATE TABLE gen_template_group (
				  group_id BIGINT NOT NULL UNIQUE,
				  template_id BIGINT NOT NULL,
				  PRIMARY KEY (group_id, template_id)
				)
				""");
		jdbc.update("INSERT INTO gen_group (id, group_name) VALUES (7, 'active')");
		reset(loader);
		TemplateUpdateManifest manifest = new TemplateUpdateManifest(1, REVISION, "bixi-default", List.of(
				new TemplateUpdateManifest.FileEntry("entity", "entity.vm", "entity.java", "b".repeat(64), 6),
				new TemplateUpdateManifest.FileEntry("mapper", "mapper.vm", "mapper.java", "c".repeat(64), 6)));
		TemplateUpdatePackageLoader.VerifiedManifest verified = new TemplateUpdatePackageLoader.VerifiedManifest(
				URI.create("https://templates.example/" + REVISION + "/"), DIGEST, manifest);
		when(loader.inspect()).thenReturn(verified);
		when(loader.load(verified)).thenReturn(new TemplateUpdatePackage(REVISION, DIGEST, "bixi-default", List.of(
				new TemplateUpdatePackage.TemplateFile("entity", "entity.vm", "entity.java", "b".repeat(64), "entity"),
				new TemplateUpdatePackage.TemplateFile("mapper", "mapper.vm", "mapper.java", "c".repeat(64), "mapper"))));
	}

	@Test
	void relationConstraintFailureRollsBackTheWholeCandidateAndPreservesTheActiveGroup() {
		assertThatThrownBy(service::onlineUpdate).isInstanceOf(RuntimeException.class);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gen_group", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT group_name FROM gen_group WHERE id = 7", String.class))
				.isEqualTo("active");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gen_template", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gen_template_group", Integer.class)).isZero();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@MapperScan(basePackageClasses = GenGroupMapper.class)
	@ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
	static class Config {
		@Bean DataSource dataSource() {
			return new DriverManagerDataSource("jdbc:h2:mem:generator-template-" + UUID.randomUUID()
					+ ";MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", "");
		}

		@Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean BixiGeneratorDefaultProperties generatorProperties() {
			BixiGeneratorDefaultProperties properties = new BixiGeneratorDefaultProperties();
			properties.setAutoCheckVersion(true);
			return properties;
		}

		@Bean TemplateUpdatePackageLoader packageLoader() {
			return mock(TemplateUpdatePackageLoader.class);
		}

		@Bean GenTemplateService genTemplateService(GenTemplateGroupMapper relationMapper,
				GenGroupMapper groupMapper, GenTemplateMapper templateMapper,
				BixiGeneratorDefaultProperties properties, TemplateUpdatePackageLoader loader) {
			GenTemplateServiceImpl service = new GenTemplateServiceImpl(relationMapper, groupMapper, properties, loader);
			ReflectionTestUtils.setField(service, "baseMapper", templateMapper);
			return service;
		}
	}
}
