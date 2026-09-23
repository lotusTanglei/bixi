package com.lotus.bixi.upms.api.service;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Transport-neutral tenant status lookup used by security filters.
 */
public interface TenantStatusService {

	TenantStatus getStatus(Long tenantId);

	record TenantStatus(Long tenantId, String status, LocalDateTime expireTime) implements Serializable {
		private static final long serialVersionUID = 1L;

		public boolean disabled() {
			return "1".equals(status);
		}

		public boolean expired(LocalDateTime now) {
			return expireTime != null && expireTime.isBefore(now);
		}
	}

}
