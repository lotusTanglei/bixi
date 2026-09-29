package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcQuarantineStoreTest {
    @Test
    void evidenceIsScopedToTheOwningInboxEvenWhenIdsCollide() throws Exception {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:quarantine_owner;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR '"
                + JdbcQuarantineStoreTest.class.getName() + ".utcTimestamp'");
        jdbc.execute("DROP TABLE IF EXISTS reliable_quarantine");
        jdbc.execute("""
                CREATE TABLE reliable_quarantine (
                    target_owner VARCHAR(64) NOT NULL,
                    evidence_id VARCHAR(128) NOT NULL,
                    body_json LONGTEXT,
                    reason VARCHAR(256) NOT NULL,
                    quarantined_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    PRIMARY KEY (target_owner, evidence_id)
                )
                """);
        var manager = new DataSourceTransactionManager(dataSource);
        var workflow = store(dataSource, manager, "workflow");
        var upms = store(dataSource, manager, "upms");

        workflow.quarantine("same-id", "{\"owner\":\"workflow\"}", "WORKFLOW_REASON");
        upms.quarantine("same-id", "{\"owner\":\"upms\"}", "UPMS_REASON");

        assertThat(workflow.list(20)).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.evidenceId()).isEqualTo("same-id");
            assertThat(snapshot.bodyJson()).contains("workflow");
        });
        assertThat(upms.list(20)).singleElement().satisfies(snapshot ->
                assertThat(snapshot.bodyJson()).contains("upms"));
        assertThat(workflow.find("same-id").bodyJson()).contains("workflow");
        assertThat(upms.find("same-id").bodyJson()).contains("upms");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reliable_quarantine", Integer.class)).isEqualTo(2);
    }

    private static JdbcQuarantineStore store(DataSource dataSource,
            DataSourceTransactionManager manager, String owner) throws Exception {
        Constructor<JdbcQuarantineStore> constructor = JdbcQuarantineStore.class.getConstructor(
                DataSource.class, org.springframework.transaction.PlatformTransactionManager.class, String.class);
        return constructor.newInstance(dataSource, manager, owner);
    }

    public static Timestamp utcTimestamp(int precision) {
        return Timestamp.from(Instant.now());
    }
}
