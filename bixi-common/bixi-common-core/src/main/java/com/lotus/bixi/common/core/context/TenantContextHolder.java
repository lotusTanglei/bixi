package com.lotus.bixi.common.core.context;

/**
 * Tenant context holder using InheritableThreadLocal for cross-thread propagation.
 *
 * @author bixi
 * @date 2026-09-21
 */
public final class TenantContextHolder {

	private static final ThreadLocal<Long> TENANT_ID = new InheritableThreadLocal<>();
	private static final ThreadLocal<Boolean> READ_ONLY_SWITCH = new InheritableThreadLocal<>();
	private static final ThreadLocal<Boolean> ALL_TENANTS_READ_ONLY = new InheritableThreadLocal<>();

	public static void set(Long tenantId) {
		TENANT_ID.set(tenantId);
	}

	public static Long get() {
		return TENANT_ID.get();
	}

	public static void setReadOnlySwitch(boolean readOnly) {
		READ_ONLY_SWITCH.set(readOnly);
	}

	public static boolean isReadOnlySwitch() {
		return Boolean.TRUE.equals(READ_ONLY_SWITCH.get());
	}

	public static void setAllTenantsReadOnly(boolean allTenantsReadOnly) {
		ALL_TENANTS_READ_ONLY.set(allTenantsReadOnly);
	}

	public static boolean isAllTenantsReadOnly() {
		return Boolean.TRUE.equals(ALL_TENANTS_READ_ONLY.get());
	}

	public static void assertWritable() {
		if (isReadOnlySwitch()) {
			throw new IllegalStateException("tenant_switch_read_only");
		}
	}

	public static void clear() {
		TENANT_ID.remove();
		READ_ONLY_SWITCH.remove();
		ALL_TENANTS_READ_ONLY.remove();
	}

	private TenantContextHolder() {
	}

}
