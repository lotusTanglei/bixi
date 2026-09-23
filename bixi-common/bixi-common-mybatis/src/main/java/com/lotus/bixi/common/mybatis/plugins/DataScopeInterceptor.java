package com.lotus.bixi.common.mybatis.plugins;

import com.lotus.bixi.common.mybatis.annotation.DataScope;
import com.lotus.bixi.common.mybatis.annotation.DataScopeType;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Properties;

@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {
		java.sql.Connection.class, Integer.class}))
public class DataScopeInterceptor implements Interceptor {

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		if (TenantContextHolder.isAllTenantsReadOnly()) return invocation.proceed();
		StatementHandler handler = (StatementHandler) invocation.getTarget();
		MetaObject handlerMeta = SystemMetaObject.forObject(handler);
		Object mapped;
		if (handlerMeta.hasGetter("delegate.mappedStatement")) {
			mapped = handlerMeta.getValue("delegate.mappedStatement");
		} else if (handlerMeta.hasGetter("mappedStatement")) {
			mapped = handlerMeta.getValue("mappedStatement");
		} else {
			return invocation.proceed();
		}
		if (!(mapped instanceof MappedStatement statement)) return invocation.proceed();
		DataScope dataScope = findAnnotation(statement.getId());
		if (dataScope == null) return invocation.proceed();
		UserIdentity identity = currentIdentity();
		if (identity == null) return invocation.proceed();
		BoundSql boundSql = handler.getBoundSql();
		String sql = boundSql.getSql();
		String condition = condition(dataScope, identity);
		if (condition == null || condition.isBlank()) return invocation.proceed();
		Statement parsed = CCJSqlParserUtil.parse(sql);
		if (!(parsed instanceof Select select) || !(select.getSelectBody() instanceof PlainSelect plainSelect)) {
			return invocation.proceed();
		}
		Expression extra = CCJSqlParserUtil.parseCondExpression(condition);
		plainSelect.setWhere(plainSelect.getWhere() == null ? extra
				: new net.sf.jsqlparser.expression.operators.conditional.AndExpression(plainSelect.getWhere(), extra));
		MetaObject metaObject = SystemMetaObject.forObject(boundSql);
		metaObject.setValue("sql", parsed.toString());
		return invocation.proceed();
	}

	private DataScope findAnnotation(String statementId) {
		int separator = statementId.lastIndexOf('.');
		if (separator < 0) return null;
		try {
			Class<?> mapper = Class.forName(statementId.substring(0, separator));
			String methodName = statementId.substring(separator + 1);
			for (Method method : mapper.getMethods()) {
				if (method.getName().equals(methodName)) return method.getAnnotation(DataScope.class);
			}
		}
		catch (ClassNotFoundException ignored) {
			// Non-interface mapped statements are not data-scoped by this interceptor.
		}
		return null;
	}

	private String condition(DataScope annotation, UserIdentity identity) {
		String user = annotation.userAlias();
		DataScopeType scope = annotation.value() == DataScopeType.SELF ? DataScopeType.fromCode(identity.dataScope) : annotation.value();
		return switch (scope) {
			case ALL -> null;
			case SELF -> user + ".id = " + identity.id;
			case DEPT -> user + ".dept_id = " + identity.deptId;
			case DEPT_AND_CHILD -> user + ".dept_id IN (SELECT descendant FROM sys_dept_relation WHERE ancestor = "
					+ identity.deptId + ")";
		};
	}

	private UserIdentity currentIdentity() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || authentication.getPrincipal() == null) return null;
		try {
			Object principal = authentication.getPrincipal();
			Long id = (Long) principal.getClass().getMethod("getId").invoke(principal);
			Long deptId = (Long) principal.getClass().getMethod("getDeptId").invoke(principal);
			String dataScope = (String) principal.getClass().getMethod("getDataScope").invoke(principal);
			return id == null || deptId == null ? null : new UserIdentity(id, deptId, dataScope);
		}
		catch (ReflectiveOperationException | ClassCastException ignored) {
			return null;
		}
	}

	private record UserIdentity(Long id, Long deptId, String dataScope) {
	}

	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	@Override
	public void setProperties(Properties properties) {
	}

}
