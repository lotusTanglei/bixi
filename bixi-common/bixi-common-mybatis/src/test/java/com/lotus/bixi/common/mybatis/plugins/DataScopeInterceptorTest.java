package com.lotus.bixi.common.mybatis.plugins;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataScopeInterceptorTest {
    @Test
    void statementHandlerWithoutMappedStatementPassesThrough() throws Throwable {
        StatementHandler handler = mock(StatementHandler.class);
        Object expected = new Object();
        Method prepare = StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);
        Invocation invocation = new Invocation(handler, prepare, new Object[]{null, null}) {
            @Override
            public Object proceed() {
                return expected;
            }
        };

        Object actual = new DataScopeInterceptor().intercept(invocation);

        assertThat(actual).isSameAs(expected);
    }
}
