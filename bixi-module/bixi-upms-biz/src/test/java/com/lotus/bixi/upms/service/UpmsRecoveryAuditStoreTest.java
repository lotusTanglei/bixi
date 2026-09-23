package com.lotus.bixi.upms.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpmsRecoveryAuditStoreTest {

    @Test
    void recordsUpmsRecoveryActionInAnIndependentTransaction() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:upms-recovery-audit;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE wf_recovery_audit (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    actor_id BIGINT NOT NULL,
                    action VARCHAR(64) NOT NULL,
                    owner VARCHAR(32) NOT NULL,
                    event_id VARCHAR(36),
                    evidence_id VARCHAR(128),
                    changed BOOLEAN NOT NULL,
                    reason VARCHAR(256) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);

        UpmsRecoveryAuditStore store = new UpmsRecoveryAuditStore(dataSource,
                new DataSourceTransactionManager(dataSource));
        store.record(7L, "INBOX_RETRY", "upms",
                "00000000-0000-0000-0000-000000000007", null, true, "manual recovery");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_recovery_audit", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT owner FROM wf_recovery_audit", String.class)).isEqualTo("upms");
        assertThat(jdbc.queryForObject("SELECT changed FROM wf_recovery_audit", Boolean.class)).isTrue();
    }
}
