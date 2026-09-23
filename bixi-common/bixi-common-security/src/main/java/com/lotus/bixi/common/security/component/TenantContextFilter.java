package com.lotus.bixi.common.security.component;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.service.TenantStatusService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import cn.hutool.extra.spring.SpringUtil;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Extracts tenant ID from the authenticated BixiUser and sets it in TenantContextHolder.
 * Clears the context after the request completes.
 *
 * @author bixi
 * @date 2026-09-21
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
				throws ServletException, IOException {
		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			Long headerTenantId = parseTenant(request.getHeader(SecurityConstants.TENANT_HEADER));
			if (headerTenantId == null) {
				headerTenantId = parseTenant(request.getHeader("TENANT-ID"));
			}
			Long principalTenantId = null;
			boolean superAdmin = false;
			if (authentication != null && authentication.getPrincipal() instanceof BixiUser bixiUser) {
				principalTenantId = bixiUser.getTenantId();
				superAdmin = bixiUser.getId() != null && bixiUser.getId().equals(1L)
						|| authentication.getAuthorities().stream().anyMatch(a -> "ROLE_SUPER_ADMIN".equals(a.getAuthority()));
			}
			boolean allTenantsRequested = "ALL".equalsIgnoreCase(request.getHeader(SecurityConstants.TENANT_SCOPE_HEADER));
			if (allTenantsRequested) {
				if (!superAdmin || principalTenantId == null) {
					writeError(response, HttpServletResponse.SC_FORBIDDEN, "tenant_scope_forbidden");
					return;
				}
				if (headerTenantId != null) {
					writeError(response, HttpServletResponse.SC_BAD_REQUEST, "tenant_scope_conflict");
					return;
				}
				TenantContextHolder.set(principalTenantId);
				TenantContextHolder.setReadOnlySwitch(true);
				TenantContextHolder.setAllTenantsReadOnly(true);
			}
			else if (principalTenantId != null) {
				if (headerTenantId != null && !principalTenantId.equals(headerTenantId) && !superAdmin) {
					writeError(response, HttpServletResponse.SC_FORBIDDEN, "tenant_mismatch");
					return;
				}
				boolean switched = headerTenantId != null && superAdmin && !principalTenantId.equals(headerTenantId);
					TenantContextHolder.set(headerTenantId != null && superAdmin ? headerTenantId : principalTenantId);
					TenantContextHolder.setReadOnlySwitch(switched);
					TenantContextHolder.setAllTenantsReadOnly(false);
				}
				else if (headerTenantId != null) {
				TenantContextHolder.set(headerTenantId);
				TenantContextHolder.setAllTenantsReadOnly(false);
			}
				else if (authentication == null || !authentication.isAuthenticated()
						|| authentication instanceof AnonymousAuthenticationToken) {
				// Keep legacy login clients working while the tenant header is optional.
				TenantContextHolder.set(SecurityConstants.DEFAULT_TENANT_ID);
				TenantContextHolder.setAllTenantsReadOnly(false);
			}
			// Actuator probes are intentionally unauthenticated and must remain independent
			// of tenant data availability (otherwise /actuator/health can deadlock startup).
			if (!isActuatorRequest(request) && TenantContextHolder.get() != null && !checkTenantStatus(response)) {
				return;
			}
			filterChain.doFilter(request, response);
		}
		finally {
			TenantContextHolder.clear();
		}
	}

	private boolean isActuatorRequest(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri != null && (uri.equals("/actuator") || uri.startsWith("/actuator/"));
	}

	private Long parseTenant(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			long tenantId = Long.parseLong(value);
			return tenantId > 0 ? tenantId : null;
		}
		catch (NumberFormatException ignored) {
			return null;
		}
	}

	private boolean checkTenantStatus(HttpServletResponse response) throws IOException {
		var services = SpringUtil.getBeansOfType(TenantStatusService.class);
		if (services.isEmpty()) {
			return true;
		}
		TenantStatusService service = services.values().stream()
				.filter(candidate -> candidate.getClass().getName().contains("SysTenantServiceImpl"))
				.findFirst().orElse(services.values().iterator().next());
		TenantStatusService.TenantStatus tenant = service.getStatus(TenantContextHolder.get());
		if (tenant == null) {
			writeError(response, HttpServletResponse.SC_FORBIDDEN, "tenant_not_found");
			return false;
		}
		if (tenant.disabled()) {
			writeError(response, HttpServletResponse.SC_FORBIDDEN, "tenant_disabled");
			return false;
		}
		if (tenant.expired(LocalDateTime.now())) {
			writeError(response, HttpServletResponse.SC_FORBIDDEN, "tenant_expired");
			return false;
		}
		return true;
	}

	private void writeError(HttpServletResponse response, int status, String code) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write("{\"code\":\"" + code + "\"}");
	}

}
