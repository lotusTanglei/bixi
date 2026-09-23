package com.lotus.bixi.common.mybatis.plugins;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.plugin.Invocation;

import java.sql.Connection;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AllTenantsReadOnlyInterceptorTest {

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsWriteSqlInAllTenantScope() throws Throwable {
        TenantContextHolder.set(1L);
        TenantContextHolder.class.getMethod("setAllTenantsReadOnly", boolean.class).invoke(null, true);
        StatementHandler handler = mock(StatementHandler.class);
        BoundSql boundSql = new BoundSql(new org.apache.ibatis.session.Configuration(),
                "UPDATE sys_user SET name = 'x'", java.util.List.of(), null);
        when(handler.getBoundSql()).thenReturn(boundSql);
        Invocation invocation = new Invocation(handler, StatementHandler.class.getMethod("prepare", Connection.class, Integer.class),
                new Object[] {mock(Connection.class), 0});

        Object interceptor = Class.forName(
                "com.lotus.bixi.common.mybatis.plugins.AllTenantsReadOnlyInterceptor")
                .getConstructor().newInstance();
        Method intercept = interceptor.getClass().getMethod("intercept", org.apache.ibatis.plugin.Invocation.class);
        assertThatThrownBy(() -> invoke(intercept, interceptor, invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("all_tenants_read_only");
    }

    private static Object invoke(Method method, Object target, Invocation invocation) throws Throwable {
        try {
            return method.invoke(target, invocation);
        }
        catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }
}
