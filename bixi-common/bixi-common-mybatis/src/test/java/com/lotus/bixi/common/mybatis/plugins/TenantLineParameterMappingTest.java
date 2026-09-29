package com.lotus.bixi.common.mybatis.plugins;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TenantLineParameterMappingTest {

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void removesMappingWhenSuppliedTenantIsReplacedByLiteral() {
		TenantContextHolder.set(7L);
		Configuration configuration = new Configuration();
		String sql = "INSERT INTO sys_log (id, title, tenant_id) VALUES (?, ?, ?)";
		List<ParameterMapping> mappings = List.of(
				new ParameterMapping.Builder(configuration, "id", Long.class).build(),
				new ParameterMapping.Builder(configuration, "title", String.class).build(),
				new ParameterMapping.Builder(configuration, "tenantId", Long.class).build());
		MappedStatement statement = new MappedStatement.Builder(configuration, "tenant.mapping",
				new StaticSqlSource(configuration, sql, mappings), SqlCommandType.INSERT).build();
		StatementHandler handler = new RoutingStatementHandler(mock(Executor.class), statement, null,
				RowBounds.DEFAULT, null, statement.getBoundSql(null));

		TenantLineInnerInterceptor interceptor = new BixiTenantLineInnerInterceptor(new BixiTenantLineHandler());
		interceptor.beforePrepare(handler, mock(Connection.class), 0);

		assertThat(handler.getBoundSql().getSql()).contains("tenant_id) VALUES (?, ?, 7)");
		assertThat(handler.getBoundSql().getParameterMappings())
				.extracting(ParameterMapping::getProperty)
				.containsExactly("id", "title");
	}
}
