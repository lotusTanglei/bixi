package com.lotus.bixi.common.core.exception;

/**
 * Thrown when tenant ID is required but not set in context.
 *
 * @author bixi
 * @date 2026-09-21
 */
public class TenantNotSetException extends RuntimeException {

	public TenantNotSetException() {
		super("Tenant ID is not set in TenantContextHolder");
	}

}
