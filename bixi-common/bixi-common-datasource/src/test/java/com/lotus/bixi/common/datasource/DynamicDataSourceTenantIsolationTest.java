package com.lotus.bixi.common.datasource;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicDataSourceTenantIsolationTest {

    @AfterEach
    void clearRoutingContext() {
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    void tenantDataSourcesReadTheirOwnRowsAndRollbackFailedWrites() {
        DataSource tenantA = source("ds-a");
        DataSource tenantB = source("ds-b");
        DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of(
                (DynamicDataSourceProvider) () -> Map.of("tenant_a", tenantA, "tenant_b", tenantB)));
        routing.setPrimary("tenant_a");
        routing.afterPropertiesSet();
        JdbcTemplate jdbc = new JdbcTemplate(routing);
        createAndSeed(jdbc, "tenant_a", "A");
        createAndSeed(jdbc, "tenant_b", "B");

        selectTenant("tenant_a");
        assertThat(jdbc.queryForObject("SELECT label FROM tenant_value", String.class)).isEqualTo("A");
        selectTenant("tenant_b");
        assertThat(jdbc.queryForObject("SELECT label FROM tenant_value", String.class)).isEqualTo("B");

        selectTenant("tenant_a");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(routing));
        assertThatThrownBy(() -> transaction.execute(status -> {
            jdbc.update("INSERT INTO tenant_value(id, label) VALUES (2, 'rollback')");
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_value", Integer.class)).isEqualTo(1);
    }

    private DataSource source(String name) {
        return new DriverManagerDataSource("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void createAndSeed(JdbcTemplate jdbc, String tenant, String value) {
        selectTenant(tenant);
        jdbc.execute("CREATE TABLE tenant_value (id INT PRIMARY KEY, label VARCHAR(32))");
        jdbc.update("INSERT INTO tenant_value(id, label) VALUES (1, ?)", value);
    }

    private void selectTenant(String tenant) {
        DynamicDataSourceContextHolder.clear();
        DynamicDataSourceContextHolder.push(tenant);
    }
}
