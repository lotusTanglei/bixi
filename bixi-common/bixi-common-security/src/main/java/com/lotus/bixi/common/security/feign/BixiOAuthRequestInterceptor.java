package com.lotus.bixi.common.security.feign;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Optional;

/**
 * oauth2 feign token传递
 * <p>
 * 重新 OAuth2FeignRequestInterceptor ，官方实现部分常见不适用
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@RequiredArgsConstructor
public class BixiOAuthRequestInterceptor implements RequestInterceptor {

    private final BearerTokenResolver tokenResolver;

    /**
     * Create a template with the header of provided name and extracted extract </br>
     * <p>
     * 1. 如果使用 非web 请求，header 区别 </br>
     * <p>
     * 2. 根据authentication 还原请求token
     *
     * @param template
     */
    @Override
    public void apply(RequestTemplate template) {
        // Never let a caller-supplied template header cross a service boundary.
        template.removeHeader(SecurityConstants.TENANT_HEADER);
        template.removeHeader("TENANT-ID");

        Collection<String> fromHeader = template.headers().get(SecurityConstants.FROM);
        boolean internalCall = CollUtil.isNotEmpty(fromHeader) && fromHeader.contains(SecurityConstants.FROM_IN);
        Optional<HttpServletRequest> request = currentRequest();
        if (request.isEmpty()) {
            // @NoToken calls can be emitted by an async worker after the servlet
            // request has been cleaned up. The listener scopes this trusted
            // context to the event before invoking Feign.
            if (internalCall) {
                Long tenantId = TenantContextHolder.get();
                if (tenantId != null && tenantId > 0) {
                    template.header(SecurityConstants.TENANT_HEADER, String.valueOf(tenantId));
                }
            }
            return;
        }
        HttpServletRequest servletRequest = request.get();

        // Only forward the value established by TenantContextFilter. A raw request
        // header must never become an authority for a downstream service.
        Long tenantId = trustedTenant(servletRequest);
        if (tenantId != null) {
            template.header(SecurityConstants.TENANT_HEADER, String.valueOf(tenantId));
        }

        // 带from 请求直接跳过
        if (internalCall) {
            return;
        }

        // 避免请求参数的 query token 无法传递
        String token = tokenResolver.resolve(servletRequest);
        if (StrUtil.isBlank(token)) {
            return;
        }
        template.header(HttpHeaders.AUTHORIZATION,
                String.format("%s %s", OAuth2AccessToken.TokenType.BEARER.getValue(), token));

    }

    private Optional<HttpServletRequest> currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return Optional.ofNullable(servletAttributes.getRequest());
        }
        return Optional.empty();
    }

    /**
     * Resolve a tenant only after the inbound filter has established a matching
     * context. Pre-authentication calls (for example password login) prove the
     * context with the validated request header; authenticated calls prove it
     * with the BixiUser principal.
     */
    private Long trustedTenant(HttpServletRequest request) {
        Long contextTenant = TenantContextHolder.get();
        if (contextTenant == null || contextTenant <= 0) {
            return null;
        }

        String rawHeader = request.getHeader(SecurityConstants.TENANT_HEADER);
        if (StrUtil.isBlank(rawHeader)) {
            rawHeader = request.getHeader("TENANT-ID");
        }
        if (StrUtil.isNotBlank(rawHeader)) {
            Long requestedTenant = parseTenant(rawHeader);
            return contextTenant.equals(requestedTenant) ? contextTenant : null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof BixiUser user
                && contextTenant.equals(user.getTenantId())) {
            return contextTenant;
        }
        return null;
    }

    private Long parseTenant(String value) {
        try {
            long tenantId = Long.parseLong(value);
            return tenantId > 0 ? tenantId : null;
        }
        catch (NumberFormatException ignored) {
            return null;
        }
    }

}
