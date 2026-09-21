package com.lotus.bixi.auth.support.filter;

import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.auth.support.handler.BixiAuthenticationFailureEventHandler;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.core.exception.ValidateCodeException;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Image captcha policy only; SMS credentials are always verified by the authentication provider. */
@Component
@RequiredArgsConstructor
public class ValidateCodeFilter extends OncePerRequestFilter {

    private final AuthSecurityConfigProperties authSecurityConfigProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!SecurityConstants.OAUTH_TOKEN_URL.equals(request.getServletPath())
                || !SecurityConstants.PASSWORD.equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            filterChain.doFilter(request, response);
            return;
        }
        var ignoreClients = authSecurityConfigProperties.getIgnoreClients();
        var principal = SecurityContextHolder.getContext().getAuthentication();
        if (ignoreClients != null && principal instanceof OAuth2ClientAuthenticationToken client
                && client.isAuthenticated() && client.getRegisteredClient() != null
                && ignoreClients.contains(client.getRegisteredClient().getClientId())) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            checkCode(request);
        } catch (ValidateCodeException exception) {
            new BixiAuthenticationFailureEventHandler().onAuthenticationFailure(request, response,
                    new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST,
                            exception.getMessage(), null)));
            return;
        }
        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private void checkCode(HttpServletRequest request) {
        String code = singleParameter(request, "code");
        String randomStr = singleParameter(request, "randomStr");
        RedisTemplate<String, Object> redis = SpringContextHolder.getBean(RedisTemplate.class);
        Object saved = redis.opsForValue().getAndDelete(CacheConstants.DEFAULT_CODE_KEY + randomStr);
        if (saved == null || !code.equals(saved.toString())) {
            throw new ValidateCodeException("验证码不合法");
        }
    }

    private String singleParameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length != 1 || StrUtil.isBlank(values[0])) {
            throw new ValidateCodeException("验证码参数不合法");
        }
        return values[0];
    }
}
