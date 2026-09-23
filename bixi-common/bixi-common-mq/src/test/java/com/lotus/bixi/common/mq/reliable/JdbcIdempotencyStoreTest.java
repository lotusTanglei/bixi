package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcIdempotencyStoreTest {

    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private JdbcTemplate jdbc;
    private JdbcIdempotencyStore store;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:idempotency;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                + JdbcIdempotencyStoreTest.class.getName() + ".utcTimestamp'");
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
        store = new JdbcIdempotencyStore(dataSource, new DataSourceTransactionManager(dataSource));
    }

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now());
    }

    @Test
    void sameKeyReplaysStableResultAndDifferentHashConflicts() {
        assertThat(store.begin(7L, "file.upload", "request-1", HASH_A, Duration.ofMinutes(5)).state())
                .isEqualTo(JdbcIdempotencyStore.State.ACQUIRED);
        assertThat(store.begin(7L, "file.upload", "request-1", HASH_A, Duration.ofMinutes(5)).state())
                .isEqualTo(JdbcIdempotencyStore.State.IN_PROGRESS);

        store.complete(7L, "file.upload", "request-1", HASH_A, 0, "{\"id\":1}");

        JdbcIdempotencyStore.Decision replay = store.begin(7L, "file.upload", "request-1", HASH_A,
                Duration.ofMinutes(5));
        assertThat(replay.state()).isEqualTo(JdbcIdempotencyStore.State.REPLAY);
        assertThat(replay.responseBody()).isEqualTo("{\"id\":1}");

        assertThat(store.begin(7L, "file.upload", "request-1", HASH_B, Duration.ofMinutes(5)).state())
                .isEqualTo(JdbcIdempotencyStore.State.CONFLICT);
    }

    @Test
    void expiredInProgressKeyCanBeAcquiredForRetry() {
        assertThat(store.begin(7L, "notice.send", "request-2", HASH_A, Duration.ofSeconds(30)).state())
                .isEqualTo(JdbcIdempotencyStore.State.ACQUIRED);
        jdbc.update("UPDATE reliable_idempotency SET expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP)");

        assertThat(store.begin(7L, "notice.send", "request-2", HASH_A, Duration.ofSeconds(30)).state())
                .isEqualTo(JdbcIdempotencyStore.State.ACQUIRED);
        assertThat(jdbc.queryForObject("SELECT attempts FROM reliable_idempotency", Integer.class)).isEqualTo(2);
    }
}
