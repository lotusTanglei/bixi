package com.lotus.bixi.common.mybatis.plugins;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BixiTenantLineHandlerTest {

	private final BixiTenantLineHandler handler = new BixiTenantLineHandler();

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	@Test
	void requiresTenantForBusinessTables() {
		assertThatThrownBy(handler::getTenantId).isInstanceOf(RuntimeException.class);
		assertThat(handler.ignoreTable("sys_tenant")).isTrue();
		assertThat(handler.ignoreTable("sys_user")).isFalse();
	}

	@Test
	void returnsCurrentTenantExpression() {
		TenantContextHolder.set(42L);
		assertThat(handler.getTenantId().toString()).isEqualTo("42");
	}

	@Test
	void allTenantReadOnlyScopeBypassesRowPredicate() throws ReflectiveOperationException {
		TenantContextHolder.set(1L);
		TenantContextHolder.class.getMethod("setAllTenantsReadOnly", boolean.class).invoke(null, true);

		assertThat(handler.ignoreTable("sys_user")).isTrue();
	}

}
