package com.lotus.bixi.common.mq.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IdempotencyExecutorTest {

    private JdbcTemplate jdbc;
    private IdempotencyExecutor executor;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:idempotency-executor;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                + IdempotencyExecutorTest.class.getName() + ".utcTimestamp'");
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
        executor = new IdempotencyExecutor(
                new JdbcIdempotencyStore(dataSource, new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), Duration.ofMinutes(5));
    }

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now());
    }

    @Test
    void replaysTheFirstResultAndRejectsAChangedRequestWithoutRepeatingTheSideEffect() throws Exception {
        AtomicInteger sideEffects = new AtomicInteger();

        String first = executor.execute(7L, "notice.save", "request-1", Map.of("title", "first"),
                String.class, () -> {
                    sideEffects.incrementAndGet();
                    return "created";
                });
        String replay = executor.execute(7L, "notice.save", "request-1", Map.of("title", "first"),
                String.class, () -> {
                    sideEffects.incrementAndGet();
                    return "should-not-run";
                });

        assertThat(first).isEqualTo("created");
        assertThat(replay).isEqualTo("created");
        assertThat(sideEffects).hasValue(1);
        assertThatThrownBy(() -> executor.execute(7L, "notice.save", "request-1",
                Map.of("title", "changed"), String.class, () -> "different"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotency_request_conflict");
    }
}
