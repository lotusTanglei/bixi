package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.ai.api.dto.ModelConfigDTO;
import com.lotus.bixi.ai.api.vo.ModelConfigVO;
import com.lotus.bixi.ai.mapper.AiModelConfigMapper;
import com.lotus.bixi.ai.service.ModelConfigService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(ModelConfigPersistenceIntegrationTest.Config.class)
@TestPropertySource(properties = {
        "ai.enabled=true",
        "mybatis-plus.global-config.banner=false"
})
class ModelConfigPersistenceIntegrationTest {

    @Autowired
    private ModelConfigService service;

    @Autowired
    private AiModelConfigMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createSchema() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        jdbc.execute("DROP TABLE IF EXISTS ai_model_config");
        jdbc.execute("""
                CREATE TABLE ai_model_config (
                    id BIGINT PRIMARY KEY,
                    current_model VARCHAR(64) NOT NULL DEFAULT 'qwen-plus',
                    temperature DECIMAL(4,3) NOT NULL DEFAULT 0.700,
                    max_tokens INT NOT NULL DEFAULT 2000,
                    top_p DECIMAL(4,3) NOT NULL DEFAULT 0.900,
                    system_prompt CLOB,
                    create_by BIGINT, update_by BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP,
                    del_flag CHAR(1) DEFAULT '0', status CHAR(1) DEFAULT '0',
                    data_status CHAR(1) DEFAULT '0', tenant_id BIGINT NOT NULL,
                    remark VARCHAR(500),
                    CONSTRAINT uk_ai_model_config_tenant UNIQUE (tenant_id)
                )
                """);
    }

    @AfterEach
    void clearContexts() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void valuesSurviveAServiceRecreationAndRemainTenantScoped() {
        login(11L, 101L);
        ModelConfigDTO first = new ModelConfigDTO();
        first.setModel("qwen-max");
        first.setSystemPrompt("tenant 101 prompt");
        first.setTemperature(1.25);
        service.updateConfig(first);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_config", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM ai_model_config", Long.class)).isEqualTo(101L);

        // A newly constructed service represents a second process after a
        // restart; it must read the shared row rather than a local map.
        ModelConfigService restarted = new ModelConfigServiceImpl(mapper);
        ModelConfigVO restored = restarted.getConfig();
        assertThat(restored.getCurrentModel()).isEqualTo("qwen-max");
        assertThat(restored.getTemperature()).isEqualTo(1.25);
        assertThat(restored.getSystemPrompt()).isEqualTo("tenant 101 prompt");

        login(22L, 202L);
        ModelConfigVO otherTenant = restarted.getConfig();
        assertThat(otherTenant.getCurrentModel()).isEqualTo("qwen-plus");
        assertThat(otherTenant.getTemperature()).isEqualTo(0.7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_config", Integer.class)).isEqualTo(1);

        ModelConfigDTO second = new ModelConfigDTO();
        second.setTemperature(1.8);
        restarted.updateConfig(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_config", Integer.class)).isEqualTo(2);

        login(11L, 101L);
        ModelConfigVO tenantOneAgain = restarted.getConfig();
        assertThat(tenantOneAgain.getCurrentModel()).isEqualTo("qwen-max");
        assertThat(tenantOneAgain.getTemperature()).isEqualTo(1.25);
    }

    @Test
    void partialUpdatePreservesStoredFieldsWithoutAReadModifyWriteRace() {
        login(31L, 303L);
        ModelConfigDTO initial = new ModelConfigDTO();
        initial.setModel("qwen-long");
        initial.setSystemPrompt("keep this prompt");
        initial.setTopP(0.35);
        service.updateConfig(initial);

        ModelConfigDTO partial = new ModelConfigDTO();
        partial.setMaxTokens(4096);
        service.updateConfig(partial);

        ModelConfigVO result = service.getConfig();
        assertThat(result.getCurrentModel()).isEqualTo("qwen-long");
        assertThat(result.getSystemPrompt()).isEqualTo("keep this prompt");
        assertThat(result.getTopP()).isEqualTo(0.35);
        assertThat(result.getMaxTokens()).isEqualTo(4096);
    }

    private static void login(long userId, long tenantId) {
        TenantContextHolder.clear();
        TenantContextHolder.set(tenantId);
        var authority = new SimpleGrantedAuthority("ai_test");
        var user = new BixiUser(userId, 1L, tenantId, "user-" + userId, "unused", null,
                true, true, true, true, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority)));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ModelConfigServiceImpl.class, MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.ai.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:ai-model-config-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
