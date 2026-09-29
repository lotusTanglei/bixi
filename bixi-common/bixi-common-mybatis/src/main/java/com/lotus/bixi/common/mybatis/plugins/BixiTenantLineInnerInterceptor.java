package com.lotus.bixi.common.mybatis.plugins;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.ParameterMapping;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * Tenant interceptor that replaces, rather than trusts, a tenant_id supplied by raw INSERT SQL.
 */
public class BixiTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

	public BixiTenantLineInnerInterceptor(TenantLineHandler tenantLineHandler) {
		super(tenantLineHandler);
	}

	@Override
	public void beforePrepare(StatementHandler statementHandler, Connection connection, Integer transactionTimeout) {
		String originalSql = statementHandler.getBoundSql().getSql();
		boolean suppliedTenant = hasSuppliedTenantColumn(originalSql);
		super.beforePrepare(statementHandler, connection, transactionTimeout);
		if (!suppliedTenant) return;

		PluginUtils.MPStatementHandler mpStatementHandler = PluginUtils.mpStatementHandler(statementHandler);
		PluginUtils.MPBoundSql boundSql = mpStatementHandler.mPBoundSql();
		List<ParameterMapping> mappings = new ArrayList<>(boundSql.parameterMappings());
		mappings.removeIf(mapping -> isTenantMapping(mapping.getProperty()));
		boundSql.parameterMappings(mappings);
	}

	@Override
	protected void processInsert(Insert insert, int index, String sql, Object obj) {
		replaceSuppliedTenant(insert);
		super.processInsert(insert, index, sql, obj);
	}

	private void replaceSuppliedTenant(Insert insert) {
		if (insert.getColumns() == null || insert.getValues() == null) return;
		List<Column> columns = insert.getColumns().getExpressions();
		int tenantIndex = -1;
		String tenantColumn = getTenantLineHandler().getTenantIdColumn();
		for (int index = 0; index < columns.size(); index++) {
			if (tenantColumn.equalsIgnoreCase(columns.get(index).getColumnName())) {
				tenantIndex = index;
				break;
			}
		}
		if (tenantIndex < 0) return;
		ExpressionList<?> values = insert.getValues().getExpressions();
		if (values == null || tenantIndex >= values.getExpressions().size()) return;
		@SuppressWarnings("unchecked")
		List<Expression> expressions = (List<Expression>) values.getExpressions();
		expressions.set(tenantIndex, getTenantLineHandler().getTenantId());
	}

	private boolean hasSuppliedTenantColumn(String sql) {
		try {
			Statement statement = CCJSqlParserUtil.parse(sql);
			if (!(statement instanceof Insert insert) || insert.getColumns() == null) return false;
			String tenantColumn = getTenantLineHandler().getTenantIdColumn();
			return insert.getColumns().stream()
					.anyMatch(column -> tenantColumn.equalsIgnoreCase(column.getColumnName()));
		}
		catch (Exception ignored) {
			return false;
		}
	}

	private boolean isTenantMapping(String property) {
		return "tenantId".equals(property) || (property != null && property.endsWith(".tenantId"));
	}

}
