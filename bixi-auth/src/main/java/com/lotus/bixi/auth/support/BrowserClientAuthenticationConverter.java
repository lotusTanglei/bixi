package com.lotus.bixi.auth.support;

import com.lotus.bixi.common.core.constant.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.Set;

public final class BrowserClientAuthenticationConverter implements AuthenticationConverter {

    public static final String CLIENT_ID = "bixi-browser";

    private static final Set<String> GRANTS = Set.of("password", "mobile", "refresh_token");

    private final String clientSecret;

    public BrowserClientAuthenticationConverter(String clientSecret) {
        if (clientSecret == null || !clientSecret.matches("[0-9a-fA-F]{48}")) {
            throw new IllegalArgumentException("security.browser-client-secret must be 48 hexadecimal characters");
        }
        this.clientSecret = clientSecret;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())
                || !SecurityConstants.OAUTH_TOKEN_URL.equals(request.getServletPath())) {
            return null;
        }

        String[] clientIds = request.getParameterValues(OAuth2ParameterNames.CLIENT_ID);
        if (clientIds == null || clientIds.length == 0 || !CLIENT_ID.equals(clientIds[0])) {
            return null;
        }

        String[] grants = request.getParameterValues(OAuth2ParameterNames.GRANT_TYPE);
        if (clientIds.length != 1 || grants == null || grants.length != 1
                || !GRANTS.contains(grants[0]) || request.getHeader(HttpHeaders.AUTHORIZATION) != null
                || request.getParameterMap().containsKey(OAuth2ParameterNames.CLIENT_SECRET)
                || request.getParameterMap().containsKey(OAuth2ParameterNames.CLIENT_ASSERTION)
                || request.getParameterMap().containsKey(OAuth2ParameterNames.CLIENT_ASSERTION_TYPE)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        return new OAuth2ClientAuthenticationToken(CLIENT_ID, ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                clientSecret, null);
    }
}
