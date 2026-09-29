package com.lotus.bixi.ai.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.ai.api.dto.DocumentDTO;
import com.lotus.bixi.ai.service.VectorStoreService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.mybatis.spring.annotation.MapperScan;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(VectorStoreProviderIntegrationTest.Config.class)
@TestPropertySource(properties = {
        "mybatis-plus.global-config.banner=false",
        "ai.enabled=true"
})
class VectorStoreProviderIntegrationTest {

    @Autowired
    private VectorStoreService documents;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createSchemaAndLogin() {
        jdbc.execute("DROP TABLE IF EXISTS ai_embedding");
        jdbc.execute("DROP TABLE IF EXISTS ai_document");
        jdbc.execute("""
                CREATE TABLE ai_document (
                    id BIGINT PRIMARY KEY, title VARCHAR(255), content CLOB, source VARCHAR(255), doc_type VARCHAR(64),
                    vector_status INT, user_id BIGINT, create_by BIGINT, update_by BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP, del_flag CHAR(1), status CHAR(1),
                    data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        jdbc.execute("""
                CREATE TABLE ai_embedding (
                    id BIGINT PRIMARY KEY, document_id BIGINT, vector_id VARCHAR(128), embedding_model VARCHAR(64),
                    embedding CLOB, dimension INT, chunk_index INT, chunk_content CLOB, create_by BIGINT, update_by BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP, del_flag CHAR(1), status CHAR(1),
                    data_status CHAR(1), tenant_id BIGINT NOT NULL, remark VARCHAR(500))
                """);
        TenantContextHolder.set(9L);
        var authorities = List.of(new SimpleGrantedAuthority("ai_document_add"));
        var user = new BixiUser(11L, 1L, 9L, "provider-failure", "unused", null,
                true, true, true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void providerFailureRollsBackDocumentAndPartialEmbeddings() {
        DocumentDTO dto = new DocumentDTO();
        dto.setTitle("provider failure");
        dto.setContent("provider failure content ".repeat(80));
        dto.setSource("test");
        dto.setDocType("txt");

        assertThatThrownBy(() -> documents.addDocument(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI embedding provider failed");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_embedding", Integer.class)).isZero();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @org.springframework.context.annotation.Import({
            MybatisAutoConfiguration.class,
            VectorStoreServiceImpl.class
    })
    @MapperScan("com.lotus.bixi.ai.mapper")
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:ai-provider-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return new EmbeddingModel() {
                @Override
                public EmbeddingResponse call(EmbeddingRequest request) {
                    throw new IllegalStateException("provider unavailable");
                }

                @Override
                public float[] embed(Document document) {
                    throw new IllegalStateException("provider unavailable");
                }

                @Override
                public List<float[]> embed(List<String> texts) {
                    throw new IllegalStateException("provider unavailable");
                }
            };
        }
    }
}
