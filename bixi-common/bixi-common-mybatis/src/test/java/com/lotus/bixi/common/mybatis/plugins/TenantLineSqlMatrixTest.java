package com.lotus.bixi.common.mybatis.plugins;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantLineSqlMatrixTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void appliesTenantPredicateToSelectUpdateAndDelete() throws Exception {
        TenantContextHolder.set(7L);

        assertThat(rewrite("SELECT id, name FROM sys_user WHERE id = 1"))
                .contains("tenant_id = 7");
        assertThat(rewrite("UPDATE sys_user SET name = 'changed' WHERE id = 1"))
                .contains("tenant_id = 7");
        assertThat(rewrite("DELETE FROM sys_user WHERE id = 1"))
                .contains("tenant_id = 7");
    }

    @Test
    void bindsTheAuthenticatedTenantToInsertStatements() throws Exception {
        TenantContextHolder.set(7L);

        assertThat(rewrite("INSERT INTO sys_user (id, name) VALUES (1, 'created')"))
                .contains("tenant_id").contains("7");
    }

    @Test
    void doesNotTrustAClientSuppliedTenantIdOnInsert() throws Exception {
        TenantContextHolder.set(7L);

        assertThat(rewrite("INSERT INTO sys_user (id, name, tenant_id) VALUES (1, 'created', 99)"))
                .doesNotContain("99")
                .contains("tenant_id").contains("7");
    }

    private String rewrite(String sql) throws Exception {
        Configuration configuration = new Configuration();
        SqlCommandType commandType = switch (sql.substring(0, sql.indexOf(' ')).toUpperCase()) {
            case "INSERT" -> SqlCommandType.INSERT;
            case "UPDATE" -> SqlCommandType.UPDATE;
            case "DELETE" -> SqlCommandType.DELETE;
            default -> SqlCommandType.SELECT;
        };
        MappedStatement statement = new MappedStatement.Builder(configuration, "tenant.matrix",
                new StaticSqlSource(configuration, sql), commandType).build();
        StatementHandler handler = new RoutingStatementHandler(mock(Executor.class), statement, null,
                RowBounds.DEFAULT, null, statement.getBoundSql(null));
        TenantLineInnerInterceptor interceptor = new BixiTenantLineInnerInterceptor(new BixiTenantLineHandler());
        if (commandType == SqlCommandType.SELECT) {
            interceptor.beforeQuery(mock(Executor.class), statement, null, RowBounds.DEFAULT, null,
                    handler.getBoundSql());
        } else {
            interceptor.beforePrepare(handler, mock(Connection.class), 0);
        }
        return handler.getBoundSql().getSql();
    }
}
