package com.lotus.bixi.common.mybatis.plugins;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

import java.sql.Connection;
import java.util.Properties;

/** Blocks writes while a platform administrator is viewing all tenants. */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
		args = {Connection.class, Integer.class}))
public class AllTenantsReadOnlyInterceptor implements org.apache.ibatis.plugin.Interceptor {

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		if (!TenantContextHolder.isAllTenantsReadOnly()) return invocation.proceed();
		StatementHandler handler = (StatementHandler) invocation.getTarget();
		Statement parsed;
		try {
			parsed = CCJSqlParserUtil.parse(handler.getBoundSql().getSql());
		}
		catch (RuntimeException invalidSql) {
			throw new IllegalStateException("all_tenants_read_only", invalidSql);
		}
		if (!(parsed instanceof Select)) {
			throw new IllegalStateException("all_tenants_read_only");
		}
		return invocation.proceed();
	}

	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	@Override
	public void setProperties(Properties properties) {
	}
}
