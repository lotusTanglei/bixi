package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

abstract class MysqlOutboxTestSupport {
    DriverManagerDataSource dataSource;
    DataSourceTransactionManager manager;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    JdbcOutboxStore store;
    ReliableDeliveryProperties properties = ReliableDeliveryProperties.defaults();

    @BeforeEach
    void prepareRealMysql() {
        dataSource = new DriverManagerDataSource(System.getenv("OUTBOX_TEST_JDBC_URL"), "root", System.getenv("MYSQL_ROOT_PASSWORD"));
        manager = new DataSourceTransactionManager(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(manager);
        store = new JdbcOutboxStore(dataSource, manager, properties);
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.0.");
        new ResourceDatabasePopulator(new FileSystemResource(Path.of(System.getProperty("outbox.test.schema",
                "../../bixi-project-documents/sql/migrations/20260921_reliable_outbox.sql"))))
                .execute(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS outbox_test_business (id INT PRIMARY KEY) ENGINE=InnoDB");
        jdbc.execute("TRUNCATE TABLE reliable_outbox");
        jdbc.execute("TRUNCATE TABLE outbox_test_business");
    }

    void enqueue(DurableMessage event, String key) {
        transaction.executeWithoutResult(status -> store.enqueue(event, key, null, null));
    }
    DurableMessage message(String owner, int value) {
        return DurableMessage.create(owner, "workflow", UUID.randomUUID().toString(), "StartRequested", 1, "{\"value\":" + value + "}");
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    String state(DurableMessage event) {
        return jdbc.queryForObject("SELECT status FROM reliable_outbox WHERE source_owner=? AND event_id=?", String.class, event.sourceOwner(), event.eventId());
    }
    void expireLeases() { jdbc.update("UPDATE reliable_outbox SET lease_until=TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))"); }
    void makeDue() { jdbc.update("UPDATE reliable_outbox SET next_attempt_at=TIMESTAMPADD(SECOND, -1, UTC_TIMESTAMP(6))"); }
    static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }
}
