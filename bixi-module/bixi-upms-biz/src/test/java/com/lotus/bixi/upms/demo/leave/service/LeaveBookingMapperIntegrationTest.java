package com.lotus.bixi.upms.demo.leave.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.upms.demo.leave.entity.LeaveBooking;
import com.lotus.bixi.upms.demo.leave.mapper.LeaveBookingMapper;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Real MyBatis and database constraints for the durable booking state machine. */
@SpringJUnitConfig(LeaveBookingMapperIntegrationTest.Config.class)
@TestPropertySource(properties = {"workflow.enabled=true", "mybatis-plus.global-config.banner=false"})
class LeaveBookingMapperIntegrationTest {
    private static final long TENANT_ID = 1L;
    private static final long LEAVE_ID = 7001L;
    private static final int ROUND = 1;
    private static final String HASH = "c".repeat(64);

    @Autowired LeaveBookingService service;
    @Autowired LeaveBookingMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT_ID);
        jdbc.execute("DROP TABLE IF EXISTS demo_leave_booking");
        jdbc.execute("""
                CREATE TABLE demo_leave_booking (
                    operation_id VARCHAR(64) NOT NULL,
                    leave_id BIGINT NOT NULL,
                    round INT NOT NULL,
                    request_hash VARCHAR(64) NOT NULL,
                    booking_state VARCHAR(16) NOT NULL,
                    booking_reference VARCHAR(128),
                    compensation_id VARCHAR(64),
                    tenant_id BIGINT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (operation_id),
                    CONSTRAINT uk_leave_booking_round UNIQUE (leave_id, round),
                    CONSTRAINT uk_leave_booking_compensation UNIQUE (compensation_id)
                )
                """);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void mapperExecutesForUpdateAndUniqueLeaveRoundConstraintIsDurable() {
        String operationId = UUID.randomUUID().toString();
        assertThat(mapper.selectByOperationIdForUpdate(operationId)).isNull();

        LeaveBooking first = new LeaveBooking();
        first.setOperationId(operationId);
        first.setLeaveId(LEAVE_ID);
        first.setRound(ROUND);
        first.setRequestHash(HASH);
        first.setBookingState("BOOKED");
        first.setBookingReference("booking-" + operationId.replace("-", ""));
        first.setCreatedAt(java.time.LocalDateTime.now());
        first.setUpdatedAt(first.getCreatedAt());
        assertThat(mapper.insert(first)).isEqualTo(1);

        assertThat(mapper.selectByOperationIdForUpdate(operationId).getBookingState()).isEqualTo("BOOKED");
        assertThat(mapper.selectByLeaveRoundForUpdate(LEAVE_ID, ROUND).getOperationId()).isEqualTo(operationId);

        LeaveBooking conflicting = new LeaveBooking();
        conflicting.setOperationId(UUID.randomUUID().toString());
        conflicting.setLeaveId(LEAVE_ID);
        conflicting.setRound(ROUND);
        conflicting.setRequestHash(HASH);
        conflicting.setBookingState("BOOKED");
        conflicting.setCreatedAt(java.time.LocalDateTime.now());
        conflicting.setUpdatedAt(conflicting.getCreatedAt());
        assertThatThrownBy(() -> mapper.insert(conflicting)).hasMessageContaining("Unique index");
    }

    @Test
    void compensationTombstoneSurvivesThroughRealMapper() {
        String operationId = UUID.randomUUID().toString();
        String compensationId = UUID.randomUUID().toString();

        LeaveBookingService.Result result = service.compensate(operationId, LEAVE_ID, ROUND, HASH, compensationId);

        assertThat(result.state()).isEqualTo("CANCELED");
        LeaveBooking persisted = mapper.selectByOperationIdForUpdate(operationId);
        assertThat(persisted.getBookingState()).isEqualTo("CANCELED");
        assertThat(persisted.getCompensationId()).isEqualTo(compensationId);
        assertThat(persisted.getBookingReference()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE leave_id = ? AND round = ?",
                Integer.class, LEAVE_ID, ROUND)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsPersistAtMostOneBookedRow() throws Exception {
        String operationId = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<LeaveBookingService.Result>> calls = List.of(
                    () -> service.request(operationId, LEAVE_ID, ROUND, HASH),
                    () -> service.request(operationId, LEAVE_ID, ROUND, HASH));
            List<Future<LeaveBookingService.Result>> futures = executor.invokeAll(calls);
            int successes = 0;
            for (Future<LeaveBookingService.Result> future : futures) {
                try {
                    assertThat(future.get(10, TimeUnit.SECONDS).state()).isEqualTo("BOOKED");
                    successes++;
                }
                catch (java.util.concurrent.ExecutionException expectedRace) {
                    // A database unique-key conflict is still a safe loser: it cannot create a second booking.
                    assertThat(expectedRace.getCause()).isInstanceOfAny(RuntimeException.class);
                }
            }
            assertThat(successes).isBetween(1, 2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE leave_id = ? AND round = ?",
                    Integer.class, LEAVE_ID, ROUND)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_leave_booking WHERE booking_state = 'BOOKED'",
                    Integer.class)).isEqualTo(1);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({LeaveBookingService.class, MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.upms.demo.leave.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            String mysqlUrl = System.getenv("WORKFLOW_TEST_JDBC_URL");
            if (mysqlUrl != null && !mysqlUrl.isBlank()) {
                if (!mysqlUrl.matches("jdbc:mysql://[^/]+/workflow_approval_test(?:\\?.*)?")) {
                    throw new IllegalArgumentException("booking integration tests require workflow_approval_test");
                }
                return new DriverManagerDataSource(mysqlUrl, System.getenv("WORKFLOW_TEST_DB_USER"),
                        System.getenv("WORKFLOW_TEST_DB_PASSWORD"));
            }
            return new DriverManagerDataSource("jdbc:h2:mem:booking-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }

        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
