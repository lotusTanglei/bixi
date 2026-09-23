package com.lotus.bixi.common.mybatis.plugins;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.exception.TenantNotSetException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;

import java.util.Set;

/**
 * MyBatis-Plus tenant line handler. Filters rows by tenant_id and auto-fills on INSERT.
 *
 * @author bixi
 * @date 2026-09-21
 */
public class BixiTenantLineHandler implements TenantLineHandler {

	private static final Set<String> IGNORE_TABLES = Set.of(
			"sys_tenant",
			"sys_user_role",
			"sys_role_menu",
			"sys_user_post",
			"sys_dept_relation",
			"sys_group_menu",
			"reliable_outbox",
			"reliable_inbox"
	);

	@Override
	public Expression getTenantId() {
		Long tenantId = TenantContextHolder.get();
		if (tenantId == null) {
			throw new TenantNotSetException();
		}
		return new LongValue(tenantId);
	}

	@Override
	public boolean ignoreTable(String tableName) {
		if (TenantContextHolder.isAllTenantsReadOnly()) {
			return true;
		}
		if (IGNORE_TABLES.contains(tableName)) {
			return true;
		}
		if (tableName.startsWith("QRTZ_") || tableName.startsWith("gen_")) {
			return true;
		}
		return false;
	}

}
