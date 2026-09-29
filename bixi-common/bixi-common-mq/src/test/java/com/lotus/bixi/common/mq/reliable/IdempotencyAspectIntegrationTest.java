package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IdempotencyAspectIntegrationTest {

    private DataSource dataSource;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:idempotency-aspect;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                + IdempotencyAspectIntegrationTest.class.getName() + ".utcTimestamp'");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS reliable_idempotency (
                    tenant_id BIGINT NOT NULL,
                    scope VARCHAR(96) NOT NULL,
                    idempotency_key VARCHAR(191) NOT NULL,
                    request_hash CHAR(64) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    response_code INT NULL,
                    response_body CLOB NULL,
                    last_error VARCHAR(256) NULL,
                    attempts INT NOT NULL DEFAULT 0,
                    expires_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (tenant_id, scope, idempotency_key)
                )
                """);
        jdbc.update("TRUNCATE TABLE reliable_idempotency");
        TenantContextHolder.set(7L);
        request = mock(HttpServletRequest.class);
        when(request.getHeader("Idempotency-Key")).thenReturn("request-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        TenantContextHolder.clear();
    }

    @Test
    void springAopReplaysTheFirstHttpWriteAndRejectsAChangedRequest() throws Exception {
        AtomicInteger sideEffects = new AtomicInteger();
        try (AnnotationConfigApplicationContext context = context(sideEffects)) {
            NoticeFacade facade = context.getBean(NoticeFacade.class);
            assertThat(AopUtils.isAopProxy(facade)).isTrue();

            assertThat(facade.save(Map.of("title", "first"))).isEqualTo("created:first");
            assertThat(facade.save(Map.of("title", "first"))).isEqualTo("created:first");
            assertThat(sideEffects).hasValue(1);
            assertThatThrownBy(() -> facade.save(Map.of("title", "changed")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("idempotency_request_conflict");
            assertThat(sideEffects).hasValue(1);
        }
    }

    private AnnotationConfigApplicationContext context(AtomicInteger sideEffects) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(AopConfiguration.class, () -> new AopConfiguration(dataSource, sideEffects));
        context.refresh();
        return context;
    }

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class AopConfiguration {

        private final AtomicInteger sideEffects;
        private final DataSource dataSource;

        AopConfiguration(DataSource dataSource, AtomicInteger sideEffects) {
            this.dataSource = dataSource;
            this.sideEffects = sideEffects;
        }

        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        IdempotencyExecutor idempotencyExecutor(DataSource dataSource, ObjectMapper objectMapper) {
            return new IdempotencyExecutor(new JdbcIdempotencyStore(dataSource,
                    new DataSourceTransactionManager(dataSource)), objectMapper, Duration.ofMinutes(5));
        }

        @Bean
        IdempotencyAspect idempotencyAspect(IdempotencyExecutor executor, ObjectMapper objectMapper) {
            return new IdempotencyAspect(executor, objectMapper);
        }

        @Bean
        NoticeFacade noticeFacade() {
            return new NoticeFacade(sideEffects);
        }
    }

    public static class NoticeFacade {
        private final AtomicInteger sideEffects;

        public NoticeFacade(AtomicInteger sideEffects) {
            this.sideEffects = sideEffects;
        }

        @Idempotent(scope = "notice.save")
        public String save(Map<String, String> request) {
            sideEffects.incrementAndGet();
            return "created:" + request.get("title");
        }
    }
}
