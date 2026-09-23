package com.lotus.bixi.auth.support.filter;

import cn.hutool.extra.spring.SpringUtil;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.service.BixiUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Comparator;
import java.util.Objects;

/**
 * Intercepts refresh_token requests to revalidate account status.
 * Prevents locked/disabled users from obtaining new tokens via refresh.
 *
 * @author 唐磊
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RefreshTokenAccountStatusFilter extends OncePerRequestFilter {

    private static final AntPathRequestMatcher TOKEN_ENDPOINT = new AntPathRequestMatcher("/oauth2/token");

    private final OAuth2AuthorizationService authorizationService;

    public RefreshTokenAccountStatusFilter(OAuth2AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isRefreshTokenRequest(request)) {
            String errorJson = validateAccountStatus(request);
            if (errorJson != null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(errorJson);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isRefreshTokenRequest(HttpServletRequest request) {
        return TOKEN_ENDPOINT.matches(request)
                && AuthorizationGrantType.REFRESH_TOKEN.getValue()
                .equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
    }

    private String validateAccountStatus(HttpServletRequest request) {
        String refreshToken = request.getParameter(OAuth2ParameterNames.REFRESH_TOKEN);
        if (refreshToken == null) {
            return null;
        }

        OAuth2Authorization authorization = authorizationService.findByToken(refreshToken,
                OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null) {
            return null;
        }

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(authorization.getAuthorizationGrantType())) {
            return null;
        }

        Object principal = authorization.getAttribute(Principal.class.getName());
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof BixiUser originalUser)) {
            return null;
        }

        UserDetails userDetails;
        try {
            var service = SpringUtil.getBeansOfType(BixiUserDetailsService.class).values().stream()
                    .filter(candidate -> candidate.support(authorization.getRegisteredClientId(),
                            authorization.getAuthorizationGrantType().getValue()))
                    .max(Comparator.comparingInt(Ordered::getOrder))
                    .orElse(null);
            if (service == null) {
                return null;
            }
            userDetails = service.loadUserByUser(originalUser);
        } catch (AuthenticationException ex) {
            return errorJson("invalid_grant", ex.getMessage());
        }

        if (!(userDetails instanceof BixiUser currentUser) || originalUser.getId() == null
                || !Objects.equals(originalUser.getId(), currentUser.getId())) {
            return errorJson("invalid_grant", "User identity changed");
        }

        try {
            new AccountStatusUserDetailsChecker().check(currentUser);
        } catch (AuthenticationException ex) {
            return errorJson("invalid_grant", ex.getMessage());
        }

        return null;
    }

    private String errorJson(String code, String description) {
        return "{\"error\":\"" + code + "\",\"error_description\":\""
                + (description != null ? description.replace("\"", "'") : "") + "\"}";
    }

}
