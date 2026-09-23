package com.lotus.bixi.common.security.component;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@RequiredArgsConstructor
public class BixiCustomOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final OAuth2AuthorizationService authorizationService;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization authorization;
        try {
            authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        } catch (RuntimeException unavailable) {
            // Storage errors can contain keys or credentials; do not expose their messages or causes.
            throw new OAuth2IntrospectionException("Authorization service unavailable");
        }
        if (authorization == null || authorization.getAccessToken() == null) {
            throw invalidToken();
        }
        var access = authorization.getAccessToken();
        var expiresAt = access.getToken().getExpiresAt();
        if (!Objects.equals(token, access.getToken().getTokenValue()) || !access.isActive()
                || expiresAt == null || !Instant.now().isBefore(expiresAt)) {
            throw invalidToken();
        }

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())) {
            Map<String, Object> attributes = new HashMap<>();
            if (access.getClaims() != null) attributes.putAll(access.getClaims());
            attributes.put(SecurityConstants.CLIENT_ID, authorization.getRegisteredClientId());
            return new DefaultOAuth2AuthenticatedPrincipal(authorization.getPrincipalName(), attributes, AuthorityUtils.NO_AUTHORITIES);
        }

        Object principal = authorization.getAttribute(Principal.class.getName());
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof BixiUser originalUser)) {
            throw invalidToken();
        }

        UserDetails userDetails;
        try {
            var service = SpringUtil.getBeansOfType(BixiUserDetailsService.class).values().stream()
                    .filter(candidate -> candidate.support(authorization.getRegisteredClientId(),
                            authorization.getAuthorizationGrantType().getValue()))
                    .max(Comparator.comparingInt(Ordered::getOrder))
                    .orElseThrow(() -> new OAuth2IntrospectionException("User authentication provider unavailable"));
            userDetails = service.loadUserByUser(originalUser);
        } catch (AuthenticationException invalidUser) {
            throw invalidToken();
        } catch (RuntimeException unavailable) {
            throw new OAuth2IntrospectionException("User authentication service unavailable");
        }

        if (!(userDetails instanceof BixiUser currentUser) || originalUser.getId() == null
                || !Objects.equals(originalUser.getId(), currentUser.getId())) {
            throw invalidToken();
        }
        try {
            new AccountStatusUserDetailsChecker().check(currentUser);
        } catch (AuthenticationException invalidUser) {
            throw invalidToken();
        }

        // Cached identities are shared across requests; client context belongs to this request only.
        var principalUser = new BixiUser(currentUser.getId(), currentUser.getDeptId(), currentUser.getTenantId(),
                currentUser.getUsername(),
                currentUser.getPassword() == null ? "" : currentUser.getPassword(), currentUser.getPhone(),
                currentUser.isEnabled(), currentUser.isAccountNonExpired(), currentUser.isCredentialsNonExpired(),
                currentUser.isAccountNonLocked(), currentUser.getAuthorities());
		principalUser.getAttributes().putAll(currentUser.getAttributes());
		principalUser.setDataScope(currentUser.getDataScope());
		principalUser.getAttributes().put(SecurityConstants.CLIENT_ID, authorization.getRegisteredClientId());
		TenantContextHolder.set(currentUser.getTenantId());
		return principalUser;
    }

    private InvalidBearerTokenException invalidToken() {
        return new InvalidBearerTokenException("Invalid bearer token");
    }

}
