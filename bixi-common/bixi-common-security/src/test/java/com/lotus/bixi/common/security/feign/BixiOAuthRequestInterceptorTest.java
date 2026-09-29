package com.lotus.bixi.common.security.feign;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class BixiOAuthRequestInterceptorTest {

    private final BearerTokenResolver tokenResolver = request -> null;

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void propagatesValidatedTenantForInternalNoTokenCallDuringLogin() {
        MockHttpServletRequest request = requestWithTenantHeader("42");
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();
        template.header(SecurityConstants.FROM, SecurityConstants.FROM_IN);

        new BixiOAuthRequestInterceptor(tokenResolver).apply(template);

        assertThat(template.headers().get(SecurityConstants.TENANT_HEADER)).containsExactly("42");
        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void propagatesPrincipalTenantWhenRequestHasNoExplicitTenantHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/user/info");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        BixiUser user = new BixiUser(7L, 3L, 42L, "tenant-user", "N/A", null,
                true, true, true, true, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();

        new BixiOAuthRequestInterceptor(tokenResolver).apply(template);

        assertThat(template.headers().get(SecurityConstants.TENANT_HEADER)).containsExactly("42");
    }

    @Test
    void dropsUntrustedTenantHeaderInsteadOfForwardingIt() {
        MockHttpServletRequest request = requestWithTenantHeader("99");
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();
        template.header(SecurityConstants.TENANT_HEADER, "99");

        new BixiOAuthRequestInterceptor(tokenResolver).apply(template);

        assertThat(template.headers()).doesNotContainKey(SecurityConstants.TENANT_HEADER);
    }

    @Test
    void doesNotForwardManuallySetTenantWithoutTrustedInboundIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();

        new BixiOAuthRequestInterceptor(tokenResolver).apply(template);

        assertThat(template.headers()).doesNotContainKey(SecurityConstants.TENANT_HEADER);
    }

    @Test
    void removesTenantHeaderWhenFeignCallRunsWithoutAWebRequest() {
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();
        template.header(SecurityConstants.TENANT_HEADER, "42");

        new BixiOAuthRequestInterceptor(tokenResolver).apply(template);

        assertThat(template.headers()).doesNotContainKey(SecurityConstants.TENANT_HEADER);
    }

    @Test
    void propagatesTrustedTenantForInternalCallWithoutAWebRequest() {
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();
        template.header(SecurityConstants.FROM, SecurityConstants.FROM_IN);

        new BixiOAuthRequestInterceptor(tokenResolver).apply(template);

        assertThat(template.headers().get(SecurityConstants.TENANT_HEADER)).containsExactly("42");
    }

    @Test
    void keepsBearerPropagationAlongsideValidatedTenant() {
        requestWithTenantHeader("42");
        TenantContextHolder.set(42L);
        RequestTemplate template = new RequestTemplate();

        new BixiOAuthRequestInterceptor(request -> "access-token").apply(template);

        assertThat(template.headers().get(SecurityConstants.TENANT_HEADER)).containsExactly("42");
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer access-token");
    }

    private MockHttpServletRequest requestWithTenantHeader(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/oauth2/token");
        request.addHeader(SecurityConstants.TENANT_HEADER, tenantId);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

}
