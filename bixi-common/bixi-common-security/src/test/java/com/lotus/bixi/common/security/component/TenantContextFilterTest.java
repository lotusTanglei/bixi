package com.lotus.bixi.common.security.component;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.upms.api.service.TenantStatusService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class TenantContextFilterTest {

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	@Test
	void anonymousAuthenticationGetsDefaultTenantBeforeOAuthClientAuthentication()
			throws ServletException, IOException {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"test-key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
		var seenTenant = new AtomicReference<Long>();
		var request = new MockHttpServletRequest();
		var response = new MockHttpServletResponse();

		try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
			spring.when(() -> SpringUtil.getBeansOfType(TenantStatusService.class)).thenReturn(Map.of());
			new TenantContextFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
					seenTenant.set(TenantContextHolder.get()));
		}

		assertThat(seenTenant).hasValue(1L);
		assertThat(TenantContextHolder.get()).isNull();
	}

	@Test
	void actuatorProbeSkipsTenantStatusLookup() throws ServletException, IOException {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"test-key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
		var request = new MockHttpServletRequest("GET", "/actuator/health");
		var response = new MockHttpServletResponse();
		var invoked = new AtomicReference<Boolean>(false);

		try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
			spring.when(() -> SpringUtil.getBeansOfType(TenantStatusService.class))
				.thenAnswer(invocation -> {
					invoked.set(true);
					return Map.of();
				});
			new TenantContextFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
				assertThat(TenantContextHolder.get()).isEqualTo(1L);
			});
		}

		assertThat(invoked).hasValue(false);
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(TenantContextHolder.get()).isNull();
	}

	@Test
	void ordinaryUserCannotRequestAllTenantScope() throws ServletException, IOException {
		var user = new com.lotus.bixi.common.security.service.BixiUser(
				42L, 7L, 42L, "tenant-user", "N/A", null, true, true, true, true,
				AuthorityUtils.createAuthorityList("ROLE_USER"));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
		var request = new MockHttpServletRequest();
		request.addHeader("X-Tenant-Scope", "ALL");
		var response = new MockHttpServletResponse();

		new TenantContextFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
				org.junit.jupiter.api.Assertions.fail("ordinary user must not enter all-tenant scope"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("tenant_scope_forbidden");
		assertThat(TenantContextHolder.get()).isNull();
	}

	@Test
	void platformAdminGetsReadOnlyAllTenantScope() throws ServletException, IOException {
		var user = new com.lotus.bixi.common.security.service.BixiUser(
				1L, 7L, 1L, "admin", "N/A", null, true, true, true, true,
				AuthorityUtils.createAuthorityList("ROLE_SUPER_ADMIN"));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
		var request = new MockHttpServletRequest();
		request.addHeader("X-Tenant-Scope", "ALL");
		var response = new MockHttpServletResponse();
		var seen = new AtomicReference<Boolean>();

		try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
			spring.when(() -> SpringUtil.getBeansOfType(TenantStatusService.class)).thenReturn(Map.of());
			new TenantContextFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
					seen.set(allTenantsReadOnly()));
		}

		assertThat(seen).hasValue(true);
		assertThat(TenantContextHolder.get()).isNull();
	}

	private static boolean allTenantsReadOnly() {
		try {
			return (Boolean) TenantContextHolder.class.getMethod("isAllTenantsReadOnly").invoke(null);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError(error);
		}
	}
}
